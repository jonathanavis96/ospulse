package com.ospulse.session;

import com.ospulse.ge.GeOfferView;
import com.ospulse.model.ItemStack;
import com.ospulse.wealth.WealthSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core banking-aware wealth-delta session tracker.
 *
 * <p>"Tracked wealth" (see {@link WealthSnapshot#tracked()}) is inventory +
 * equipment + GE-in-flight + pouches. While away from a bank, any change in
 * tracked wealth is real profit/loss. Bank visits are transfers of cold
 * storage into/out of tracked wealth and must not register as profit — not
 * even transiently. While the bank is open its value is live, and the bank's
 * change since open is exactly the net amount transferred (nothing else can
 * move the bank), so profit adds that change back on top of tracked wealth,
 * making every deposit/withdrawal zero-sum in real time. On close the same
 * net transfer is folded into the profit {@code baseline} permanently, so the
 * figure is continuous across the close. Genuine gains while the bank happens
 * to be open (e.g. a GE fill) don't move the bank and therefore still count.
 *
 * <p>"Profit" is realised activity only (loot, trade P&amp;L, genuine quantity
 * changes) — passive price drift on held items must never move it. Each held
 * tracked item carries a session cost basis (live price when its units were
 * first seen / acquired this session, average-cost across later buys, see
 * {@link #syncCostBasis}); the gap between a holding's live value and its
 * basis is "unrealized P/L". The two figures always sum to the raw
 * mark-to-market delta, so selling (or consuming/banking) a holding moves its
 * paper gain into realised profit with no jump in the total.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
@Slf4j
public final class SessionEngine
{
	/**
	 * When true, the per-update wealth/attribution diagnostics below log at INFO
	 * instead of DEBUG, so they appear in RuneLite's default (INFO) client.log
	 * without changing the root log level — a dev aid for diagnosing profit
	 * misattribution (e.g. a phantom gain on a gear swap). Driven by the
	 * {@code verboseDiagnostics} config via {@code SessionTracker}.
	 */
	private boolean verboseDiagnostics;

	/** Tracked wealth that currently defines zero profit. */
	private long baseline;
	private long startMs;
	private boolean bankOpen;
	private long trackedAtBankOpen;
	/**
	 * Live bank value captured when the current bank visit began (or the
	 * first moment during the visit the bank value became known). The bank's
	 * movement away from this anchor is the net amount deposited so far.
	 */
	private long bankValueAtOpen;
	/** Whether {@link #bankValueAtOpen} has been captured for this visit. */
	private boolean bankValueAtOpenKnown;
	/**
	 * Grace window (ms) after a bank close during which a movement of the
	 * bank's value observed while the bank is CLOSED is still treated as part
	 * of the visit's net transfer. RuneLite can deliver the bank-container
	 * update AFTER the widget-closed event, so the close-time reanchor reads a
	 * stale bank value and under-neutralises the visit (observed in the wild:
	 * a 22.98M withdrawal whose bank update landed ~2s after the close booked
	 * as +22.98M phantom profit, permanently). Movements seen inside this
	 * window are folded into the baseline exactly as the close reanchor would
	 * have; see {@link #reconcileClosedBankMovement}.
	 */
	private static final long BANK_CLOSE_TRANSFER_GRACE_MS = 5_000L;
	/**
	 * Last bank value seen while the bank was closed — the anchor
	 * {@link #reconcileClosedBankMovement} measures closed-bank movements
	 * from. Valid only while {@link #closedBankAnchorKnown}.
	 */
	private long closedBankAnchor;
	private boolean closedBankAnchorKnown;
	/** Timestamp of the last bank close; meaningful only while {@link #closeGraceArmed}. */
	private long lastBankCloseTsMs;
	/** True once a bank visit has closed with a known bank value, arming the grace window. */
	private boolean closeGraceArmed;
	private WealthSnapshot previous;
	/**
	 * Loot aggregated per item across the whole session (like the Loot Tracker
	 * plugin): itemId -&gt; running total quantity + value. Unbounded — one entry
	 * per distinct item, not one per pickup — so a stream of the same drop
	 * (e.g. thousands of bird nests) collapses into a single summarised row.
	 */
	private final Map<Integer, LootEntry> lootTotals = new LinkedHashMap<>();
	/**
	 * Running session total (gp value) of consumable supplies used: tracked
	 * items whose quantity decreased while the bank was closed and which
	 * weren't attributed to a GE sale (see {@link #update}). This is a
	 * conservative heuristic, not a perfect classification — see {@link
	 * #update} for the exact exclusion rules and their limitations.
	 */
	private long suppliesUsed;
	private long startNetWorth;
	private boolean startBankKnown;
	/**
	 * itemId -&gt; session cost basis for every currently-held tracked item
	 * (quantity + total acquisition cost in gp). Kept in lockstep with the
	 * latest snapshot's tracked items by {@link #syncCostBasis}.
	 */
	private final Map<Integer, Basis> costBasis = new LinkedHashMap<>();
	/**
	 * Figures from the previous {@link #snapshot} call, kept only so the
	 * per-snapshot debug log can report a delta. {@code null} until the first
	 * snapshot is produced. Never affects profit/unrealized computation.
	 */
	private DebugFigures lastLoggedFigures;
	/**
	 * Wall-clock window (ms) within which a stack vanishing in one update and
	 * reappearing in the next (or vice versa) is treated as the two halves of
	 * a single equip/unequip rather than genuine consumption followed by loot.
	 * RuneLite fires one ItemContainerChanged per container, so mid-equip the
	 * engine can observe the stack in NEITHER container (inventory already
	 * updated, equipment not yet) or in BOTH (the reverse order) for one
	 * refresh; the two events land within the same client tick, so one game
	 * tick is a comfortably safe bound.
	 */
	private static final long TRANSFER_REVERSAL_WINDOW_MS = 600L;
	/**
	 * Wall-clock window (ms) within which a WHOLE stack that vanished from
	 * tracked wealth may reappear (same item id, whole-stack appearance of up
	 * to the vanished quantity) and be netted against the recorded vanish
	 * instead of booking as fresh loot. Covers real "wealth parked outside
	 * tracked containers" round trips the one-tick transient window cannot:
	 * loading a dwarf multicannon moves the whole cannonball stack out of the
	 * inventory and picking the cannon up returns the unfired remainder
	 * minutes later, and assembling the cannon does the same with its four
	 * ~190k parts (observed in the wild: a 34,805-ball / 9.19M load rebooked
	 * as 9.19M of fresh "loot" on return). Without netting, the return
	 * inflates the loot feed — and since cannonballs classify as consumable
	 * supplies, the round trip books the full stack value as supplies AND
	 * again as loot, inflating profit by the whole stack. Bounded so a
	 * genuinely consumed stack whose twin is genuinely looted much later
	 * still counts as loot. Bank visits clear the pending records (see
	 * {@link #setBankOpen}), so this never nets across a restock.
	 */
	private static final long VANISH_RETURN_WINDOW_MS = 30L * 60_000L;
	/**
	 * Vanishes classified by PREVIOUS updates (supplies charged / value
	 * silently dropped) that a later update may reverse if the stack
	 * reappears — see {@link #update}. Partial-stack entries are reversible
	 * for exactly one following interval (and only within
	 * {@link #TRANSFER_REVERSAL_WINDOW_MS}); whole-stack entries are retained
	 * and stay reversible for {@link #VANISH_RETURN_WINDOW_MS} so a stack
	 * parked outside tracked containers (cannon load, cannon parts) is netted
	 * on return instead of booking as loot.
	 */
	private List<PendingSwing> pendingVanished = new ArrayList<>();
	/** Loot recorded by the PREVIOUS update, reversible symmetrically. */
	private List<PendingSwing> pendingLooted = new ArrayList<>();

	/**
	 * One classified quantity swing (vanish or loot) kept for one interval so
	 * the next update can reverse it if it turns out to be the first half of
	 * an equip/unequip transient.
	 */
	private static final class PendingSwing
	{
		final int itemId;
		/** Remaining reversible quantity; decremented as reversals consume it. */
		long quantity;
		/** Unit value the swing was recorded (and, if charged, priced) at. */
		final long unitValue;
		/** Whether the whole stack swung (fully vanished / fully new). */
		final boolean fullSwing;
		/** Vanish side: {@link #recordSupplyConsumed} was applied and must be undone. */
		final boolean suppliesCharged;
		/** Cost-basis state just before the swing, to restore on full reversal. */
		final boolean hadBasis;
		final long basisQuantity;
		final long basisTotalCost;
		final long tsMs;

		PendingSwing(int itemId, long quantity, long unitValue, boolean fullSwing,
			boolean suppliesCharged, boolean hadBasis, long basisQuantity, long basisTotalCost, long tsMs)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.unitValue = unitValue;
			this.fullSwing = fullSwing;
			this.suppliesCharged = suppliesCharged;
			this.hadBasis = hadBasis;
			this.basisQuantity = basisQuantity;
			this.basisTotalCost = basisTotalCost;
			this.tsMs = tsMs;
		}
	}

	/**
	 * A quantity delta observed by the current update, pre-classification.
	 * Mutable {@code quantity} is whittled down by transfer pairing and
	 * reversal netting; whatever survives is classified as loot/supplies.
	 */
	private static final class Swing
	{
		final int itemId;
		final String name;
		long quantity;
		final long unitValue;
		final boolean fullSwing;
		final boolean consumable;
		final boolean hadBasis;
		final long basisQuantity;
		final long basisTotalCost;

		Swing(int itemId, String name, long quantity, long unitValue, boolean fullSwing,
			boolean consumable, Basis basisOrNull)
		{
			this.itemId = itemId;
			this.name = name;
			this.quantity = quantity;
			this.unitValue = unitValue;
			this.fullSwing = fullSwing;
			this.consumable = consumable;
			this.hadBasis = basisOrNull != null;
			this.basisQuantity = basisOrNull == null ? 0L : basisOrNull.quantity;
			this.basisTotalCost = basisOrNull == null ? 0L : basisOrNull.totalCost;
		}
	}

	/**
	 * Deferred cost-basis correction applied after {@link #syncCostBasis} has
	 * run for the update: carries a holding's session basis across an id swap
	 * or an equip transient instead of letting the units re-enter at the live
	 * price (which would silently convert paper drift into realised profit).
	 * Applied only if the item is actually held at {@code expectedQuantity},
	 * so a partial/foreign state can never be corrupted.
	 */
	private static final class BasisCarry
	{
		final int itemId;
		final long expectedQuantity;
		final long totalCost;

		BasisCarry(int itemId, long expectedQuantity, long totalCost)
		{
			this.itemId = itemId;
			this.expectedQuantity = expectedQuantity;
			this.totalCost = totalCost;
		}
	}

	/**
	 * Parsed "<base> (<dose>)" shape of a dose-suffixed item name (potions,
	 * brews, restores — anything named "...(1)" through "...(4)"), used only
	 * to pair a dose-down within the same update (see {@link #update}). Not
	 * related to {@link SupplyClassifier}'s broader consumable matching.
	 */
	private static final class DoseName
	{
		private static final java.util.regex.Pattern PATTERN =
			java.util.regex.Pattern.compile("^(?<base>.+?)\\s*\\((?<dose>[1-4])\\)$",
				java.util.regex.Pattern.CASE_INSENSITIVE);

		final String base;
		final int dose;

		private DoseName(String base, int dose)
		{
			this.base = base;
			this.dose = dose;
		}

		/** @return the parsed base/dose, or {@code null} if not dose-suffixed. */
		static DoseName parse(String name)
		{
			if (name == null)
			{
				return null;
			}
			java.util.regex.Matcher m = PATTERN.matcher(name.trim());
			if (!m.matches())
			{
				return null;
			}
			return new DoseName(m.group("base").trim().toLowerCase(java.util.Locale.ROOT),
				Integer.parseInt(m.group("dose")));
		}
	}

	/** Immutable bundle of the debug-loggable figures from one snapshot. */
	private static final class DebugFigures
	{
		final long netWorthDelta;
		final long profit;
		final long unrealizedPnl;

		DebugFigures(long netWorthDelta, long profit, long unrealizedPnl)
		{
			this.netWorthDelta = netWorthDelta;
			this.profit = profit;
			this.unrealizedPnl = unrealizedPnl;
		}
	}

	/** Mutable per-item cost-basis accumulator (quantity held + total cost). */
	private static final class Basis
	{
		long quantity;
		long totalCost;
	}

	/**
	 * Begins a new session. Resets baseline, start time, loot, and banking
	 * state.
	 */
	public void startSession(WealthSnapshot initial, long tsMs)
	{
		if (diagEnabled())
		{
			logDiag("[reanchor] session RESET baseline {} -> {} startNetWorth {} -> {} bankOpenWasTrue={}",
				this.baseline, initial.tracked(), this.startNetWorth, initial.netWorth(), this.bankOpen);
		}
		this.baseline = initial.tracked();
		this.startMs = tsMs;
		this.previous = initial;
		this.startNetWorth = initial.netWorth();
		this.startBankKnown = initial.isBankKnown();
		this.bankOpen = false;
		this.trackedAtBankOpen = 0L;
		this.bankValueAtOpen = 0L;
		this.bankValueAtOpenKnown = false;
		this.closedBankAnchorKnown = initial.isBankKnown();
		this.closedBankAnchor = initial.isBankKnown() ? initial.getBankValue() : 0L;
		this.closeGraceArmed = false;
		this.lastBankCloseTsMs = 0L;
		this.lootTotals.clear();
		this.suppliesUsed = 0L;
		this.costBasis.clear();
		this.lastLoggedFigures = null;
		this.pendingVanished = new ArrayList<>();
		this.pendingLooted = new ArrayList<>();
		// Holdings present at session start enter at their live price, so
		// unrealized P/L starts the session at zero.
		syncCostBasis(initial);
	}

	/**
	 * Reports a bank-open/close transition. On open, records the tracked
	 * wealth and live bank value at that instant. On close, folds the net
	 * amount transferred during the visit (measured by the bank's own change,
	 * see {@link #bankVisitBaselineShift}) into the baseline, so
	 * deposits/withdrawals never register as profit or loss — matching the
	 * live offset {@link #snapshot} applied while the bank was open, so the
	 * profit figure is continuous across the close. No loot diffing happens
	 * while the bank is open (see {@link #update}).
	 */
	public void setBankOpen(boolean open, WealthSnapshot current, long tsMs)
	{
		syncCostBasis(current);
		// Catch any closed-bank movement (lagged transfer from the previous
		// visit, or a revaluation) delivered in the same batch as this
		// transition, BEFORE this visit anchors to the current bank value.
		reconcileClosedBankMovement(current, tsMs);
		if (!this.bankOpen && open)
		{
			// FALSE -> TRUE: bank just opened. Anchor the visit.
			this.trackedAtBankOpen = current.tracked();
			this.bankValueAtOpenKnown = current.isBankKnown();
			this.bankValueAtOpen = current.isBankKnown() ? current.getBankValue() : 0L;
			if (diagEnabled())
			{
				logDiag("[reanchor] bank OPEN tracked={} bankValueAtOpen={}/known={} baseline={}",
					trackedAtBankOpen, bankValueAtOpen, bankValueAtOpenKnown, baseline);
			}
		}
		else if (this.bankOpen && !open)
		{
			// TRUE -> FALSE: bank just closed. Make the visit's net transfer
			// permanently neutral.
			long oldBaseline = this.baseline;
			long shift = bankVisitBaselineShift(current);
			this.baseline += shift;
			if (diagEnabled())
			{
				logDiag("[reanchor] bank CLOSE netTransferShift={} baseline {} -> {}",
					shift, oldBaseline, this.baseline);
			}
			// The bank-container update for a transfer made at the very end of
			// the visit can arrive AFTER this close event; anchor the close-time
			// bank value and keep reconciling briefly (see
			// reconcileClosedBankMovement) so the late tail of the transfer is
			// still neutralised instead of booking as phantom profit.
			this.closedBankAnchorKnown = current.isBankKnown();
			this.closedBankAnchor = current.isBankKnown() ? current.getBankValue() : 0L;
			this.closeGraceArmed = current.isBankKnown();
			this.lastBankCloseTsMs = tsMs;
		}

		this.previous = current;
		this.bankOpen = open;
		// A bank visit re-anchors all diffing; stale equip-transient records
		// from before the visit must never net against post-visit changes.
		this.pendingVanished = new ArrayList<>();
		this.pendingLooted = new ArrayList<>();
	}

	/**
	 * Baseline shift that neutralises the current bank visit at close time.
	 * The bank's change since open is exactly the net amount deposited
	 * (nothing but transfers can move the bank while it is open), so shifting
	 * the baseline by minus that change keeps a pure transfer at zero profit
	 * while leaving genuine tracked-wealth gains made during the visit
	 * counted. Falls back to neutralising the whole tracked-wealth change
	 * (the pre-live behaviour) only if the bank value somehow never became
	 * visible during the visit.
	 */
	private long bankVisitBaselineShift(WealthSnapshot current)
	{
		if (bankValueAtOpenKnown && current.isBankKnown())
		{
			return -(current.getBankValue() - bankValueAtOpen);
		}
		return current.tracked() - trackedAtBankOpen;
	}

	/**
	 * If the bank value only became known partway through the current visit,
	 * anchor {@link #bankValueAtOpen} at its first known reading (treating
	 * the blind interval as transfer-free). Idempotent.
	 */
	private void captureLateKnownBank(WealthSnapshot current)
	{
		if (bankOpen && !bankValueAtOpenKnown && current.isBankKnown())
		{
			this.bankValueAtOpen = current.getBankValue();
			this.bankValueAtOpenKnown = true;
		}
	}

	/**
	 * While the bank is open, the net amount deposited so far this visit
	 * (withdrawals negative); added to tracked wealth it makes inventory/bank
	 * transfers zero-sum in real time. Zero while the bank is closed — after
	 * a close the same amount lives in the baseline instead (see
	 * {@link #setBankOpen}).
	 */
	private long liveBankTransferOffset(WealthSnapshot current)
	{
		if (!bankOpen || !bankValueAtOpenKnown || !current.isBankKnown())
		{
			return 0L;
		}
		return current.getBankValue() - bankValueAtOpen;
	}

	/**
	 * Books any movement of the bank's value observed while the bank is
	 * CLOSED. Nothing this engine models can move a closed bank, so a change
	 * is one of two things:
	 * <ul>
	 *   <li><b>A lagged transfer</b> — RuneLite can deliver the bank-container
	 *       update after the widget-closed event, so the tail of the visit's
	 *       net transfer lands moments after {@link #setBankOpen} already
	 *       reconciled the visit. Within {@link #BANK_CLOSE_TRANSFER_GRACE_MS}
	 *       of the last close the movement is folded into the baseline exactly
	 *       as {@link #bankVisitBaselineShift} would have at close time.</li>
	 *   <li><b>A passive revaluation</b> — the bank is valued at live prices,
	 *       so a GE price reload can move a large bank by millions with no
	 *       transfer at all (observed: +4.09M on a ~1.1B bank). That paper
	 *       drift is folded into {@link #startNetWorth} (mirroring
	 *       {@link #foldNewlyKnownBankIntoStart}), so the "Net worth Δ"
	 *       reflects session activity rather than price drift on cold
	 *       storage, and the accounting identity
	 *       {@code netWorthDelta == profit - suppliesUsed + unrealizedPnl}
	 *       survives the reload. An out-of-band deposit this engine doesn't
	 *       model (e.g. GE collect-to-bank) is also folded here — the
	 *       matching tracked-wealth movement books to profit/supplies, so the
	 *       identity still holds; only the transfer's label is lost.</li>
	 * </ul>
	 * Anchor-based and idempotent: safe to call from {@link #update},
	 * {@link #snapshot} and {@link #setBankOpen} in any order and repeatedly.
	 * No-op while the bank is open (movements are live transfers handled by
	 * {@link #liveBankTransferOffset}) or while the bank value is unknown.
	 */
	private void reconcileClosedBankMovement(WealthSnapshot current, long tsMs)
	{
		if (bankOpen || !current.isBankKnown())
		{
			return;
		}
		if (!closedBankAnchorKnown)
		{
			this.closedBankAnchor = current.getBankValue();
			this.closedBankAnchorKnown = true;
			return;
		}
		long delta = current.getBankValue() - closedBankAnchor;
		if (delta == 0)
		{
			return;
		}
		this.closedBankAnchor = current.getBankValue();
		if (closeGraceArmed && tsMs - lastBankCloseTsMs <= BANK_CLOSE_TRANSFER_GRACE_MS)
		{
			long oldBaseline = this.baseline;
			this.baseline -= delta;
			if (diagEnabled())
			{
				logDiag("[reanchor] late bank update {}ms after close: transferShift={} baseline {} -> {}",
					tsMs - lastBankCloseTsMs, -delta, oldBaseline, this.baseline);
			}
		}
		else
		{
			long oldStart = this.startNetWorth;
			this.startNetWorth += delta;
			if (diagEnabled())
			{
				logDiag("[reanchor] closed-bank revaluation {}: startNetWorth {} -> {}",
					delta, oldStart, this.startNetWorth);
			}
		}
	}

	/** Enables/disables promoting the per-update wealth/attribution diagnostics to INFO — see {@link #verboseDiagnostics}. */
	public void setVerboseDiagnostics(boolean enabled)
	{
		this.verboseDiagnostics = enabled;
	}

	/** Logs a diagnostic line at INFO when {@link #verboseDiagnostics} is on (dev), else DEBUG (default). */
	private void logDiag(String format, Object... args)
	{
		if (verboseDiagnostics)
		{
			log.info(format, args);
		}
		else
		{
			log.debug(format, args);
		}
	}

	/**
	 * Whether the guarded diagnostic breakdowns should be built at all: true
	 * when verbose diagnostics is on (they log at INFO) OR DEBUG is enabled.
	 * Used to skip the (cheap) string/figure assembly when it would go nowhere.
	 */
	private boolean diagEnabled()
	{
		return verboseDiagnostics || log.isDebugEnabled();
	}

	/**
	 * Advances the session with a new wealth snapshot. While the bank is
	 * open, this is a no-op besides recording {@code current} as the new
	 * baseline for future diffing (banking = transfers, not loot/supplies).
	 * While away, any positive quantity delta in tracked items that isn't
	 * attributed to a GE transaction and has a positive unit value is
	 * recorded as a loot event; any NEGATIVE quantity delta in a tracked
	 * item that {@link SupplyClassifier} recognises as a consumable, and
	 * that isn't attributed to a GE sale, is recorded as supplies used (see
	 * {@link #getSuppliesUsed}) and its value is folded into {@link
	 * #baseline} — exactly like a bank-visit transfer — so eating/drinking a
	 * supply is excluded from profit rather than registering as a loss;
	 * "supplies used" is where that spend shows up instead.
	 *
	 * <p>Equipping/unequipping is ZERO-SUM: gear moving between the inventory
	 * and equipment components of tracked wealth must never register as loot,
	 * supplies or profit. Two equip shapes would otherwise leak through the
	 * per-id delta diff and are explicitly neutralised here:
	 * <ul>
	 *   <li><b>Id swap</b> — the worn form of an item carries a different id
	 *       than the inventory form, so one id disappears and another of equal
	 *       quantity and unit value appears in the same interval. Such a pair
	 *       is matched up-front and treated as an internal transfer (no loot,
	 *       no supplies, cost basis migrated to the new id).</li>
	 *   <li><b>Container-event transient</b> — RuneLite fires one
	 *       ItemContainerChanged per container, so mid-equip the stack can be
	 *       observed in NEITHER container (or in BOTH) for one refresh. The
	 *       vanish half is classified normally (it is indistinguishable from
	 *       consumption at that instant), but is remembered for one interval;
	 *       if the stack reappears within {@link #TRANSFER_REVERSAL_WINDOW_MS}
	 *       the classification is reversed (supplies un-charged, baseline
	 *       restored, no loot, cost basis restored). The symmetric
	 *       both-containers order (phantom gain then correction) is netted the
	 *       same way via the remembered loot records.</li>
	 * </ul>
	 *
	 * <p>Limitation: this is a conservative heuristic, not a perfect
	 * consumed-vs-transferred classification. Because it only runs while the
	 * bank is closed and skips GE-attributed items, depositing potions/ammo
	 * into the bank or selling them on the GE is correctly excluded.
	 * However a decrease caused by some other out-of-band transfer this
	 * engine doesn't model (e.g. trading the item to another player,
	 * or a rare bank action that doesn't toggle {@code bankOpen}) would be
	 * mis-counted as "used". This mirrors the existing loot heuristic, which
	 * has the same blind spot for gains. Conversely, consuming a stack and
	 * regaining the identical stack within the same ~one-tick reversal window
	 * nets to nothing — economically a wash, and vastly cheaper than the
	 * phantom profit the netting prevents.
	 *
	 * @param geAttributedItemIds item ids whose quantity changed this
	 *                            interval due to a GE buy/sell, supplied by
	 *                            the integration layer (see
	 *                            {@code GeReconciler#drainAttributedItemIds()});
	 *                            these are excluded from both the loot feed
	 *                            and supplies-used tracking.
	 */
	public void update(WealthSnapshot current, Set<Integer> geAttributedItemIds, long tsMs)
	{
		if (bankOpen)
		{
			syncCostBasis(current);
			captureLateKnownBank(current);
			logUpdateBreakdown(current, 0L, 0L, 0L, 0L, 0L, 0L);
			this.previous = current;
			return;
		}

		reconcileClosedBankMovement(current, tsMs);

		long trackedBefore = previous == null ? current.tracked() : previous.tracked();
		long baselineBefore = baseline;

		Map<Integer, ItemStack> previousItems = previous == null
			? Collections.emptyMap()
			: previous.getTrackedItems();

		// ---- 1. Collect this interval's raw quantity swings (pre-classification).
		// Cost-basis state is captured here, BEFORE syncCostBasis mutates it,
		// so transfers/reversals can carry a holding's basis across the move.
		List<Swing> appeared = new ArrayList<>();
		List<Swing> vanished = new ArrayList<>();
		for (Map.Entry<Integer, ItemStack> entry : current.getTrackedItems().entrySet())
		{
			int itemId = entry.getKey();
			ItemStack currentStack = entry.getValue();
			ItemStack previousStack = previousItems.get(itemId);
			long previousQty = previousStack == null ? 0L : previousStack.getQuantity();
			long delta = currentStack.getQuantity() - previousQty;

			if (delta == 0)
			{
				continue;
			}
			if (geAttributedItemIds != null && geAttributedItemIds.contains(itemId))
			{
				logAttribution(itemId, currentStack.getName(), delta, delta * currentStack.getUnitValue(),
					"GE(reconciled, excluded from loot/supplies)");
				continue;
			}
			if (currentStack.getUnitValue() <= 0)
			{
				logAttribution(itemId, currentStack.getName(), delta, 0L, "SKIP(no unit value)");
				continue;
			}

			if (delta > 0)
			{
				appeared.add(new Swing(itemId, currentStack.getName(), delta,
					currentStack.getUnitValue(), previousQty == 0,
					false, costBasis.get(itemId)));
			}
			else
			{
				vanished.add(new Swing(itemId, currentStack.getName(), -delta,
					currentStack.getUnitValue(), false,
					SupplyClassifier.isConsumable(currentStack.getName()), costBasis.get(itemId)));
			}
		}

		// Items present in the previous snapshot but entirely absent from the
		// current one (last unit of a stack consumed/dropped/sold — snapshots
		// only carry entries with quantity > 0, see SessionTracker) are
		// invisible to the loop above, which only walks current's items.
		// Without this pass, drinking the final dose of a potion or eating the
		// last piece of food would silently reduce profit with no
		// supplies-used attribution at all.
		for (Map.Entry<Integer, ItemStack> entry : previousItems.entrySet())
		{
			int itemId = entry.getKey();
			if (current.getTrackedItems().containsKey(itemId))
			{
				continue;
			}
			ItemStack previousStack = entry.getValue();
			if (geAttributedItemIds != null && geAttributedItemIds.contains(itemId))
			{
				logAttribution(itemId, previousStack.getName(), -previousStack.getQuantity(),
					-previousStack.getQuantity() * previousStack.getUnitValue(),
					"GE(reconciled, excluded from loot/supplies)");
				continue;
			}
			if (previousStack.getUnitValue() <= 0)
			{
				logAttribution(itemId, previousStack.getName(), -previousStack.getQuantity(), 0L,
					"SKIP(no unit value)");
				continue;
			}
			vanished.add(new Swing(itemId, previousStack.getName(), previousStack.getQuantity(),
				previousStack.getUnitValue(), true,
				SupplyClassifier.isConsumable(previousStack.getName()), costBasis.get(itemId)));
		}

		// ---- 2. Same-interval transfer pairing (equip id-swap): a whole stack
		// of id A gone and a whole stack of id B — equal quantity, equal unit
		// value — new in the SAME interval is the same item changing id, not
		// consumption + loot. Neither side is classified; the basis migrates.
		List<BasisCarry> basisCarries = new ArrayList<>();
		long transferPaired = 0L;
		for (Swing v : vanished)
		{
			if (!v.fullSwing || v.quantity == 0)
			{
				continue;
			}
			for (Swing a : appeared)
			{
				if (a.quantity == 0 || !a.fullSwing || a.itemId == v.itemId
					|| a.quantity != v.quantity || a.unitValue != v.unitValue)
				{
					continue;
				}
				transferPaired += a.quantity * a.unitValue;
				if (v.hadBasis && v.basisQuantity == v.quantity)
				{
					basisCarries.add(new BasisCarry(a.itemId, a.quantity, v.basisTotalCost));
				}
				logAttribution(v.itemId, v.name, -v.quantity, -v.quantity * v.unitValue, "TRANSFER(id-swap out)");
				logAttribution(a.itemId, a.name, a.quantity, a.quantity * a.unitValue, "TRANSFER(id-swap in)");
				a.quantity = 0L;
				v.quantity = 0L;
				break;
			}
		}

		// ---- 2b. Same-interval dose-swap pairing: drinking a dose of a
		// potion (or similar "(n)" charge item) makes the higher-dose stack
		// vanish and a lower-dose stack of a DIFFERENT item id appear in the
		// SAME interval, e.g. "Super antifire potion(4)" -> "...(3)". Unlike
		// the id-swap transfer pairing above, the two sides have UNEQUAL unit
		// value (a lower dose is worth less), so they never match there and
		// the appearing lower-dose stack would otherwise fall through to loot
		// — silently inflating profit by the value of every dose drunk. Only
		// the value actually consumed (higher-dose unit value minus
		// lower-dose unit value, times the doses drunk) is booked as supplies
		// via the normal recordSupplyConsumed path; neither side is booked as
		// loot for the paired portion.
		for (Swing v : vanished)
		{
			DoseName vDose = DoseName.parse(v.name);
			if (vDose == null || v.quantity == 0)
			{
				continue;
			}
			for (Swing a : appeared)
			{
				if (a.quantity == 0)
				{
					continue;
				}
				DoseName aDose = DoseName.parse(a.name);
				if (aDose == null || !aDose.base.equals(vDose.base) || aDose.dose >= vDose.dose)
				{
					continue;
				}

				long n = Math.min(v.quantity, a.quantity);
				long consumedValue = n * (v.unitValue - a.unitValue);
				if (consumedValue > 0)
				{
					recordSupplyConsumed(consumedValue);
				}
				logAttribution(v.itemId, v.name, -n, -consumedValue, "DOSE-SWAP");
				logAttribution(a.itemId, a.name, n, 0L, "DOSE-SWAP(residual dose, no charge)");
				a.quantity -= n;
				v.quantity -= n;
				break;
			}
		}

		// ---- 3. Reversal netting against the PREVIOUS interval (equip
		// container-event transient). An appearance first cancels a matching
		// just-recorded vanish (un-charging supplies and restoring the
		// baseline) before anything counts as loot; a disappearance first
		// retracts matching just-recorded loot before anything counts as
		// supplies. Matching prefers the same item id (the usual same-id
		// flicker) and falls back to an exact whole-stack value signature
		// (id-swap split across the two events).
		long suppliesReversed = 0L;
		long lootRecorded = 0L;
		List<PendingSwing> newPendingLooted = new ArrayList<>();
		for (Swing a : appeared)
		{
			for (int pass = 0; pass < 2 && a.quantity > 0; pass++)
			{
				for (PendingSwing p : pendingVanished)
				{
					long ageMs = tsMs - p.tsMs;
					if (p.quantity == 0 || ageMs > VANISH_RETURN_WINDOW_MS)
					{
						continue;
					}
					boolean withinTransientWindow = ageMs <= TRANSFER_REVERSAL_WINDOW_MS;
					// Pass 0: same item id. Within the one-tick transient window
					// any same-id reappearance nets (equip flicker); beyond it,
					// only a whole-stack vanish answered by a whole-stack
					// reappearance of up to the vanished quantity nets (a stack
					// parked outside tracked containers coming back, e.g. a
					// cannon load returned on pickup — see
					// VANISH_RETURN_WINDOW_MS). Pass 1: id-swap split across two
					// container events, transient window only.
					boolean matched = pass == 0
						? p.itemId == a.itemId
							&& (withinTransientWindow
								|| (p.fullSwing && a.fullSwing && a.quantity <= p.quantity))
						: (withinTransientWindow
							&& p.itemId != a.itemId && a.fullSwing && p.fullSwing
							&& p.quantity == a.quantity && p.unitValue == a.unitValue);
					if (!matched)
					{
						continue;
					}
					long reversedQty = Math.min(a.quantity, p.quantity);
					boolean fullReversal = reversedQty == p.quantity;
					if (p.suppliesCharged)
					{
						long reversedValue = reversedQty * p.unitValue;
						suppliesUsed -= reversedValue;
						this.baseline += reversedValue;
						suppliesReversed += reversedValue;
						logAttribution(a.itemId, a.name, reversedQty, reversedValue,
							"REVERSAL-NET(un-charge supply, transient/returned stack)");
					}
					else
					{
						logAttribution(a.itemId, a.name, reversedQty, reversedQty * p.unitValue,
							"REVERSAL-NET(transient/returned stack, guarded from loot)");
					}
					a.quantity -= reversedQty;
					p.quantity -= reversedQty;
					if (fullReversal && p.hadBasis)
					{
						ItemStack heldNow = current.getTrackedItems().get(a.itemId);
						if (heldNow != null && heldNow.getQuantity() == p.basisQuantity)
						{
							basisCarries.add(new BasisCarry(a.itemId, p.basisQuantity, p.basisTotalCost));
						}
					}
					if (a.quantity == 0)
					{
						break;
					}
				}
			}

			if (a.quantity > 0)
			{
				long lootValue = a.quantity * a.unitValue;
				addLoot(new LootEntry(a.itemId, a.name, a.quantity, lootValue, tsMs));
				lootRecorded += lootValue;
				logAttribution(a.itemId, a.name, a.quantity, lootValue, "LOOT");
				newPendingLooted.add(new PendingSwing(a.itemId, a.quantity, a.unitValue,
					a.fullSwing, false, a.hadBasis, a.basisQuantity, a.basisTotalCost, tsMs));
			}
		}

		long lootReversed = 0L;
		long suppliesRecorded = 0L;
		List<PendingSwing> newPendingVanished = new ArrayList<>();
		for (Swing v : vanished)
		{
			for (int pass = 0; pass < 2 && v.quantity > 0; pass++)
			{
				for (PendingSwing p : pendingLooted)
				{
					if (p.quantity == 0 || tsMs - p.tsMs > TRANSFER_REVERSAL_WINDOW_MS)
					{
						continue;
					}
					boolean matched = pass == 0
						? p.itemId == v.itemId
						: (p.itemId != v.itemId && v.fullSwing && p.fullSwing
							&& p.quantity == v.quantity && p.unitValue == v.unitValue);
					if (!matched)
					{
						continue;
					}
					long reversedQty = Math.min(v.quantity, p.quantity);
					boolean fullReversal = reversedQty == p.quantity;
					long reversedValue = reversedQty * p.unitValue;
					retractLoot(p.itemId, reversedQty, reversedValue);
					lootReversed += reversedValue;
					logAttribution(v.itemId, v.name, -reversedQty, -reversedValue,
						"REVERSAL-NET(retract loot, equip transient)");
					v.quantity -= reversedQty;
					p.quantity -= reversedQty;
					if (fullReversal)
					{
						if (p.itemId == v.itemId && p.hadBasis)
						{
							// Same-id flicker (gain then correction): restore
							// the pre-gain basis instead of the scaled one.
							basisCarries.add(new BasisCarry(p.itemId, p.basisQuantity, p.basisTotalCost));
						}
						else if (p.itemId != v.itemId && v.hadBasis && v.basisQuantity == reversedQty)
						{
							// Id-swap split across events (new id appeared
							// first): the surviving new id inherits the
							// vanished id's basis.
							ItemStack heldNow = current.getTrackedItems().get(p.itemId);
							if (heldNow != null && heldNow.getQuantity() == reversedQty)
							{
								basisCarries.add(new BasisCarry(p.itemId, reversedQty, v.basisTotalCost));
							}
						}
					}
					if (v.quantity == 0)
					{
						break;
					}
				}
			}

			if (v.quantity > 0)
			{
				boolean charge = v.consumable;
				if (charge)
				{
					long consumedValue = v.quantity * v.unitValue;
					recordSupplyConsumed(consumedValue);
					suppliesRecorded += consumedValue;
					logAttribution(v.itemId, v.name, -v.quantity, -consumedValue, "SUPPLY");
				}
				else
				{
					logAttribution(v.itemId, v.name, -v.quantity, -v.quantity * v.unitValue,
						"VANISH(untracked, not booked as supply or loot)");
				}
				// Remember ALL surviving vanishes (consumable or not) so a
				// reappearance next update is netted instead of read as loot.
				newPendingVanished.add(new PendingSwing(v.itemId, v.quantity, v.unitValue,
					v.fullSwing, charge, v.hadBasis, v.basisQuantity, v.basisTotalCost, tsMs));
			}
		}

		// ---- 4. Commit. Loot records and partial-stack vanishes roll forward
		// exactly one interval (the equip-transient window); whole-stack
		// vanishes from earlier intervals are retained while still reversible
		// within VANISH_RETURN_WINDOW_MS so a parked stack's return can be
		// netted (oldest first, ahead of this update's records).
		List<PendingSwing> retainedVanished = new ArrayList<>();
		for (PendingSwing p : pendingVanished)
		{
			if (p.quantity > 0 && p.fullSwing && tsMs - p.tsMs <= VANISH_RETURN_WINDOW_MS)
			{
				retainedVanished.add(p);
			}
		}
		retainedVanished.addAll(newPendingVanished);
		this.pendingVanished = retainedVanished;
		this.pendingLooted = newPendingLooted;

		syncCostBasis(current);
		for (BasisCarry carry : basisCarries)
		{
			Basis basis = costBasis.get(carry.itemId);
			if (basis != null && basis.quantity == carry.expectedQuantity)
			{
				basis.totalCost = carry.totalCost;
			}
		}

		long trackedDelta = current.tracked() - trackedBefore;
		long baselineDelta = baseline - baselineBefore;
		logUpdateBreakdown(current, trackedDelta, lootRecorded, suppliesRecorded,
			lootReversed + transferPaired, suppliesReversed, trackedDelta - baselineDelta);

		this.previous = current;
	}

	/**
	 * Guarded per-update wealth-breakdown DEBUG log so a "profit jumped for no
	 * reason" report can be audited from client.log: every component of
	 * tracked wealth, the bank, what this update classified (loot/supplies and
	 * any equip-transient reversals), and the resulting realised-profit
	 * movement ({@code m2mDelta} = Δtracked − Δbaseline, i.e. how far this
	 * update moved {@code markToMarket} — the profit+unrealized total).
	 */
	private void logUpdateBreakdown(WealthSnapshot current, long trackedDelta,
		long lootRecorded, long suppliesRecorded, long lootReversed, long suppliesReversed,
		long m2mDelta)
	{
		if (!diagEnabled())
		{
			return;
		}
		logDiag("update: tracked={} (inv={} equip={} ge={} pouch={}) bank={}/known={} bankOpen={} "
				+ "Δtracked={} loot+={} supplies+={} lootReversed={} suppliesReversed={} baseline={} ΔprofitM2m={}",
			current.tracked(), current.getInventoryValue(), current.getEquipmentValue(),
			current.getGeInFlightValue(), current.getPouchValue(),
			current.getBankValue(), current.isBankKnown(), bankOpen,
			trackedDelta, lootRecorded, suppliesRecorded, lootReversed, suppliesReversed,
			baseline, m2mDelta);
	}

	/**
	 * Guarded compact per-item attribution DEBUG line for a single quantity
	 * swing classified during {@link #update}, e.g.
	 * {@code [attrib] id=560 name='Death rune' dq=-42 val=-8.4k -> SUPPLY}.
	 * Purely diagnostic — never affects any profit/wealth computation. Value
	 * is the signed gp value of the swing (quantity * unit value, negated for
	 * vanishes) so a bucket's total is scannable at a glance.
	 */
	private void logAttribution(int itemId, String name, long signedQty, long signedValue, String bucket)
	{
		// Skip building the line only when it would go nowhere — i.e. neither the
		// verbose-diagnostics INFO path nor DEBUG is active. (Verbose mode is the
		// whole point of surfacing per-item [attrib] lines under the INFO default.)
		if (!diagEnabled())
		{
			return;
		}
		logDiag("[attrib] id={} name='{}' dq={} val={} -> {}",
			itemId, name, signedQty, signedValue, bucket);
	}

	/**
	 * Removes {@code quantity}/{@code value} of a previously recorded loot row
	 * (an equip-transient reversal, see {@link #update}), deleting the row
	 * outright once nothing remains.
	 */
	private void retractLoot(int itemId, long quantity, long value)
	{
		LootEntry existing = lootTotals.get(itemId);
		if (existing == null)
		{
			return;
		}
		long newQty = existing.getQuantity() - quantity;
		long newValue = existing.getValue() - value;
		if (newQty <= 0 && newValue <= 0)
		{
			lootTotals.remove(itemId);
			return;
		}
		lootTotals.put(itemId, new LootEntry(itemId, existing.getName(),
			Math.max(0L, newQty), Math.max(0L, newValue), existing.getTimestampMs()));
	}

	/**
	 * Records {@code consumedValue} gp of a consumable leaving tracked wealth
	 * via consumption (eating/drinking), and neutralises it against profit.
	 * Folds the value into {@link #baseline} — exactly like a bank-visit
	 * transfer is neutralised in {@link #setBankOpen} — so {@code
	 * markToMarket = tracked() - baseline} (and hence profit) is unaffected
	 * by the consumed value, while {@link #suppliesUsed} still surfaces it as
	 * its own line item.
	 */
	private void recordSupplyConsumed(long consumedValue)
	{
		suppliesUsed += consumedValue;
		this.baseline -= consumedValue;
	}

	/**
	 * Brings {@link #costBasis} into lockstep with {@code current}'s tracked
	 * items. Acquired units (quantity above the basis) enter at the live
	 * price — for loot that's the pickup value, for a GE buy it's the price
	 * at fill time. Departing units (sold, consumed, or banked) carry away
	 * their proportional share of the total cost (average-cost accounting),
	 * so a partial sell leaves the remaining units' basis intact. Items no
	 * longer held are dropped — cost basis is tracked-wealth-scoped, so
	 * banking a holding settles its paper gain at the deposit-time valuation
	 * (the bank itself is valued at live prices, keeping profit continuous).
	 * Idempotent for an unchanged snapshot.
	 */
	private void syncCostBasis(WealthSnapshot current)
	{
		Map<Integer, ItemStack> items = current.getTrackedItems();
		costBasis.keySet().removeIf(itemId -> !items.containsKey(itemId));

		for (Map.Entry<Integer, ItemStack> entry : items.entrySet())
		{
			ItemStack stack = entry.getValue();
			long quantity = stack.getQuantity();
			if (quantity <= 0)
			{
				costBasis.remove(entry.getKey());
				continue;
			}

			Basis basis = costBasis.computeIfAbsent(entry.getKey(), id -> new Basis());
			if (quantity > basis.quantity)
			{
				basis.totalCost += (quantity - basis.quantity) * stack.getUnitValue();
			}
			else if (quantity < basis.quantity)
			{
				// Double intermediate: totalCost * quantity can overflow long
				// for huge stacks; values are well within double's exact
				// integer range (2^53).
				basis.totalCost = Math.round((double) basis.totalCost * quantity / basis.quantity);
			}
			basis.quantity = quantity;
		}
	}

	/**
	 * Per-holding unrealized P/L rows for {@code current}'s tracked items
	 * (live value minus session cost basis), ordered by absolute unrealized
	 * amount descending. Must be called after {@link #syncCostBasis} so every
	 * held item has a basis at the current quantity.
	 */
	private List<HoldingPnl> holdingPnls(WealthSnapshot current)
	{
		List<HoldingPnl> rows = new ArrayList<>();
		for (ItemStack stack : current.getTrackedItems().values())
		{
			Basis basis = costBasis.get(stack.getId());
			if (basis == null)
			{
				continue;
			}
			rows.add(new HoldingPnl(stack.getId(), stack.getName(), stack.getQuantity(),
				basis.totalCost, stack.getQuantity() * stack.getUnitValue()));
		}
		rows.sort((a, b) -> Long.compare(Math.abs(b.unrealized()), Math.abs(a.unrealized())));
		return rows;
	}

	private void addLoot(LootEntry entry)
	{
		LootEntry existing = lootTotals.get(entry.getItemId());
		if (existing == null)
		{
			lootTotals.put(entry.getItemId(), entry);
		}
		else
		{
			lootTotals.put(entry.getItemId(), new LootEntry(
				entry.getItemId(),
				entry.getName(),
				existing.getQuantity() + entry.getQuantity(),
				existing.getValue() + entry.getValue(),
				entry.getTimestampMs()));
		}
	}

	/**
	 * Aggregated loot, one row per item, ordered by total value descending
	 * (most valuable first) — the summarised view the panel renders.
	 */
	private List<LootEntry> lootSummary()
	{
		List<LootEntry> summary = new ArrayList<>(lootTotals.values());
		summary.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		return summary;
	}

	/**
	 * Produces a read-only snapshot of the session's current state given the
	 * latest wealth reading and externally-tracked GE/XP data. No active-offer
	 * breakdown (delegates with an empty offer list).
	 */
	public SessionSnapshot snapshot(
		WealthSnapshot current,
		long geRealizedPnl,
		Map<String, Long> xpGained,
		long xpTotal,
		long tsMs)
	{
		return snapshot(current, geRealizedPnl, Collections.emptyList(),
			Collections.emptyList(), xpGained, xpTotal, tsMs);
	}

	/**
	 * Produces a read-only snapshot of the session's current state given the
	 * latest wealth reading and externally-tracked GE/XP/loot-source data.
	 */
	public SessionSnapshot snapshot(
		WealthSnapshot current,
		long geRealizedPnl,
		List<GeOfferView> geOffers,
		List<SourceLoot> lootSources,
		Map<String, Long> xpGained,
		long xpTotal,
		long tsMs)
	{
		foldNewlyKnownBankIntoStart(current);
		captureLateKnownBank(current);
		reconcileClosedBankMovement(current, tsMs);
		syncCostBasis(current);

		// While the bank is open, add back the net amount deposited so far so
		// in-progress transfers are zero-sum immediately rather than only
		// after the bank closes.
		long transferOffset = liveBankTransferOffset(current);
		long markToMarket = current.tracked() + transferOffset - baseline;

		// Split the raw mark-to-market delta: paper gains on current holdings
		// (live value vs cost basis) are unrealized; whatever remains is
		// realised activity — loot, trade P&L, genuine quantity changes.
		// Price drift moves only the unrealized side.
		List<HoldingPnl> holdingPnls = holdingPnls(current);
		long unrealizedPnl = 0L;
		for (HoldingPnl row : holdingPnls)
		{
			unrealizedPnl += row.unrealized();
		}
		long profit = markToMarket - unrealizedPnl;

		long elapsedMs = tsMs - startMs;
		long profitPerHour = elapsedMs > 0 ? profit * 3600000L / elapsedMs : 0L;
		long netWorthDelta = current.netWorth() - startNetWorth;

		if (diagEnabled())
		{
			// Guarded wealth breakdown so a "profit jumped for no reason"
			// report can be fact-checked from client.log. Includes the delta
			// vs the previous snapshot's realised/unrealized/net-worth figures
			// so a suspicious jump is visible without diffing log lines by hand.
			DebugFigures prev = lastLoggedFigures;
			long profitDelta = prev == null ? 0L : profit - prev.profit;
			long unrealizedDelta = prev == null ? 0L : unrealizedPnl - prev.unrealizedPnl;
			long netWorthDeltaDelta = prev == null ? 0L : netWorthDelta - prev.netWorthDelta;
			logDiag("wealth: tracked={} (inv={} equip={} ge={} pouch={}) bank={}/known={} "
					+ "bankOpen={} transferOffset={} baseline={} m2m={} realised={} unrealized={} netWorthDelta={} "
					+ "| Δrealised={} Δunrealized={} ΔnetWorthDelta={}",
				current.tracked(), current.getInventoryValue(), current.getEquipmentValue(),
				current.getGeInFlightValue(), current.getPouchValue(),
				current.getBankValue(), current.isBankKnown(),
				bankOpen, transferOffset, baseline, markToMarket, profit, unrealizedPnl, netWorthDelta,
				profitDelta, unrealizedDelta, netWorthDeltaDelta);
			lastLoggedFigures = new DebugFigures(netWorthDelta, profit, unrealizedPnl);
		}

		return new SessionSnapshot(
			startMs,
			elapsedMs,
			profit,
			profitPerHour,
			geRealizedPnl,
			netWorthDelta,
			current.isBankKnown(),
			lootSummary(),
			xpGained,
			xpTotal,
			current,
			geOffers,
			lootSources,
			Collections.emptyList(),
			0L,
			null,
			unrealizedPnl,
			holdingPnls,
			suppliesUsed);
	}

	/**
	 * The bank value is unknown until the bank is first opened this session, so
	 * the session's starting net worth was captured tracked-only. The instant
	 * the bank becomes known we retroactively fold its value into the starting
	 * net worth, so the "Net worth Δ" reflects the newly-visible bank as having
	 * been there all along rather than jumping by the whole bank balance (which
	 * looked like a huge phantom gain the first time the bank was opened).
	 */
	private void foldNewlyKnownBankIntoStart(WealthSnapshot current)
	{
		if (!startBankKnown && current.isBankKnown())
		{
			long oldStartNetWorth = startNetWorth;
			startNetWorth += current.getBankValue();
			startBankKnown = true;
			if (diagEnabled())
			{
				logDiag("[reanchor] bank newly known, folding into startNetWorth: bankValue={} startNetWorth {} -> {}",
					current.getBankValue(), oldStartNetWorth, startNetWorth);
			}
		}
	}

	// Accessors below are useful for tests/inspection; state remains
	// otherwise fully encapsulated.

	public long getBaseline()
	{
		return baseline;
	}

	public boolean isBankOpen()
	{
		return bankOpen;
	}

	public List<LootEntry> getLoot()
	{
		return Collections.unmodifiableList(lootSummary());
	}

	public boolean isStartBankKnown()
	{
		return startBankKnown;
	}

	/**
	 * Running session total (gp value) of consumable supplies used so far —
	 * see {@link #update} for exactly what counts.
	 */
	public long getSuppliesUsed()
	{
		return suppliesUsed;
	}
}
