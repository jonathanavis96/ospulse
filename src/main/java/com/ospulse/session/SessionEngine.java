package com.ospulse.session;

import com.ospulse.ge.GeAttributions;
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
 * even transiently. A bank transfer conserves total net worth (tracked +
 * bank); a genuine gain (e.g. a GE fill while the bank happens to be open)
 * changes it — that is the invariant everything below protects.
 *
 * <p>RuneLite updates the inventory container instantly but the bank
 * container's value can lag by several seconds and arrives as a separate
 * event, so the two halves of one transfer are often observed in DIFFERENT
 * snapshots — potentially straddling the visit's close, or even a later
 * visit, when the player opens/closes the bank faster than the lag. The
 * engine therefore keeps ONE continuous bank-value anchor and classifies
 * every observed bank movement exactly once, at the moment it is observed
 * (see {@link #reconcileBankMovement}): while the bank is open the movement
 * is a live transfer folded straight into the {@code baseline} (making a
 * settled deposit/withdrawal zero-sum in real time, with nothing left to do
 * at close); while closed it is a lagged transfer tail if it settles a
 * pending in-flight expectation or lands within the post-close grace window,
 * else a passive revaluation. The in-flight expectations
 * ({@link #pendingBankSettles}) hold a deposit's fresh tracked-side drop out
 * of profit until its lagged bank-side rise lands, so a transfer stays
 * zero-sum even mid-lag; genuine gains never involve the bank side and keep
 * counting immediately.
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
	/**
	 * Tracked wealth at the moment the current visit opened. Only used by the
	 * blind-visit fallback at close time: if the bank value never became
	 * visible during the visit, the whole tracked-wealth change is
	 * neutralised instead (the pre-live behaviour), since no bank-side
	 * measurement ever existed.
	 */
	private long trackedAtBankOpen;
	/** Whether the bank value has been visible at any point during the current visit. */
	private boolean visitSawBank;
	/**
	 * Grace window (ms) after a bank close during which a movement of the
	 * bank's value observed while the bank is CLOSED is still treated as a
	 * lagged transfer tail of the visit. RuneLite can deliver the
	 * bank-container update AFTER the widget-closed event (observed in the
	 * wild: a 22.98M withdrawal whose bank update landed ~2s after the close
	 * booked as +22.98M phantom profit, permanently). This is the only
	 * discriminator available for the WITHDRAWAL direction (a bank drop);
	 * deposits are additionally covered — beyond this window too — by their
	 * in-flight expectation in {@link #pendingBankSettles}.
	 */
	private static final long BANK_CLOSE_TRANSFER_GRACE_MS = 5_000L;
	/**
	 * Last observed bank value — the single continuous anchor
	 * {@link #reconcileBankMovement} measures ALL bank movements from,
	 * whether the bank is open or closed. One anchor means every movement is
	 * classified exactly once; the previous per-visit open anchor plus a
	 * separate closed anchor let a lagged movement that straddled an
	 * open/close boundary be booked by BOTH channels, oscillating the
	 * baseline by the full transfer amount on every rapid open/close cycle
	 * (observed: a 57.1M shuffle flip-flopping the baseline for seconds).
	 * Valid only while {@link #bankAnchorKnown}.
	 */
	private long bankAnchor;
	private boolean bankAnchorKnown;
	/** Timestamp of the last bank close; meaningful only while {@link #closeGraceArmed}. */
	private long lastBankCloseTsMs;
	/** True once a bank visit has closed with a known bank value, arming the grace window. */
	private boolean closeGraceArmed;
	/**
	 * How long (ms) a one-sided tracked-wealth drop observed while banking is
	 * held as the in-flight half of a transfer (see
	 * {@link #pendingBankSettles}) before it expires and books as the genuine
	 * loss it then must be. Comfortably above the largest bank-container lag
	 * seen in the wild (~3.6s), while still bounding how long a real loss can
	 * be masked.
	 */
	private static final long BANK_TRANSFER_SETTLE_WINDOW_MS = 10_000L;
	/**
	 * In-flight deposit expectations: value that left tracked wealth while
	 * the bank was open without a matching bank-side rise in the same
	 * observation. The wealth is still owned — it is merely between container
	 * events — so the total is added back into both the mark-to-market and
	 * the net-worth delta until the bank-side rise lands (settling the entry,
	 * see {@link #reconcileBankMovement}), an offsetting tracked-side rise
	 * cancels it (equip flicker / an in-flight withdrawal netting against it,
	 * see {@link #trackOpenTrackedSwing}), or it expires after
	 * {@link #BANK_TRANSFER_SETTLE_WINDOW_MS}. FIFO.
	 */
	private final List<PendingBankSettle> pendingBankSettles = new ArrayList<>();
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

	/** One in-flight deposit expectation — see {@link #pendingBankSettles}. */
	private static final class PendingBankSettle
	{
		/** Remaining unsettled value; whittled down by settles/cancels. */
		long amount;
		final long tsMs;

		PendingBankSettle(long amount, long tsMs)
		{
			this.amount = amount;
			this.tsMs = tsMs;
		}
	}

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
		this.visitSawBank = false;
		this.bankAnchorKnown = initial.isBankKnown();
		this.bankAnchor = initial.isBankKnown() ? initial.getBankValue() : 0L;
		this.pendingBankSettles.clear();
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
	 * wealth at that instant (blind-visit fallback anchor only — bank
	 * movements themselves are folded into the baseline continuously by
	 * {@link #reconcileBankMovement} as their readings arrive, so a visit
	 * needs no bank-value anchor of its own and nothing transfer-related is
	 * left to reconcile at close). On close, arms the post-close grace window
	 * for the visit's still-lagging bank updates, and — only if the bank
	 * value was never visible during the whole visit — falls back to
	 * neutralising the visit's entire tracked-wealth change. No loot diffing
	 * happens while the bank is open (see {@link #update}).
	 */
	public void setBankOpen(boolean open, WealthSnapshot current, long tsMs)
	{
		syncCostBasis(current);
		// Whether the bank had been visible during the visit BEFORE this
		// transition's own observation (the pre-rewrite semantics: a bank
		// value first revealed by the close snapshot itself never yielded a
		// usable bank-side measurement, so the blind fallback still applies).
		boolean sawBankBeforeTransition = this.visitSawBank;
		// Book any bank movement delivered in the same batch as this
		// transition against the state the movement was observed in (still
		// open for a close, still closed for an open).
		long bankDelta = reconcileBankMovement(current, tsMs);
		if (!this.bankOpen && open)
		{
			// FALSE -> TRUE: bank just opened. Anchor the blind-visit fallback.
			this.trackedAtBankOpen = current.tracked();
			this.visitSawBank = current.isBankKnown();
			if (diagEnabled())
			{
				logDiag("[reanchor] bank OPEN tracked={} bankKnown={} baseline={}",
					trackedAtBankOpen, current.isBankKnown(), baseline);
			}
		}
		else if (this.bankOpen && !open)
		{
			// TRUE -> FALSE: bank just closed. The close snapshot can carry a
			// last-instant deposit whose bank side is still in flight — hold
			// it pending exactly like a mid-visit one.
			trackOpenTrackedSwing(current, bankDelta, tsMs);
			if (!sawBankBeforeTransition)
			{
				// The bank value never became visible during the visit, so no
				// bank-side measurement of the visit's transfers ever existed
				// (or ever will): neutralise the whole tracked-wealth change
				// instead (the pre-live behaviour; genuine gains made during
				// such a blind visit are swallowed too — accepted).
				long oldBaseline = this.baseline;
				this.baseline += current.tracked() - trackedAtBankOpen;
				if (diagEnabled())
				{
					logDiag("[reanchor] bank CLOSE (blind visit) trackedShift={} baseline {} -> {}",
						current.tracked() - trackedAtBankOpen, oldBaseline, this.baseline);
				}
			}
			else if (diagEnabled())
			{
				logDiag("[reanchor] bank CLOSE baseline={} pendingSettle={}", baseline, pendingSettleTotal());
			}
			// Transfers made at the very end of the visit can have their
			// bank-container update arrive AFTER this close event; keep
			// treating closed-bank movements as transfer tails briefly (see
			// reconcileBankMovement) so the late tail is still neutralised
			// instead of booking as phantom profit.
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
	 * Books the movement of the bank's value since the last observation —
	 * the single classification point for ALL bank movements, open or
	 * closed, so a movement whose delivery straddles an open/close boundary
	 * (or lands during a completely different visit) is still booked exactly
	 * once. Classification:
	 * <ul>
	 *   <li><b>While the bank is open</b> — nothing but transfers is modelled
	 *       to move an open bank, so the movement is a live transfer of value
	 *       to/from tracked wealth and is folded into the {@code baseline}
	 *       immediately. A settled deposit/withdrawal is thereby zero-sum in
	 *       real time, and nothing remains to reconcile at close. Note the
	 *       movement may belong to an EARLIER visit whose reading lagged into
	 *       this one — the fold is identical either way, which is exactly why
	 *       the single anchor cannot double-book what the old per-visit
	 *       anchors did.</li>
	 *   <li><b>While closed, a rise that settles an in-flight deposit</b>
	 *       (see {@link #pendingBankSettles}) — the lagged half of a deposit
	 *       whose tracked-side drop is already being held out of profit:
	 *       folded into the baseline (permanently neutralising the deposit)
	 *       and the expectation consumed, so the settle itself moves nothing.
	 *       This works however late the reading lands, unlike the grace
	 *       window below.</li>
	 *   <li><b>While closed, within {@link #BANK_CLOSE_TRANSFER_GRACE_MS} of
	 *       the last close</b> — a lagged transfer tail (this is the only
	 *       available discriminator for a withdrawal's bank-side drop, which
	 *       has no expectation ledger: a tracked-wealth RISE while banking is
	 *       observationally identical to a genuine gain, e.g. a GE fill, and
	 *       gains must keep counting immediately): folded into the
	 *       baseline.</li>
	 *   <li><b>Otherwise a passive revaluation</b> — the bank is valued at
	 *       live prices, so a GE price reload can move a large closed bank by
	 *       millions with no transfer at all (observed: +4.09M on a ~1.1B
	 *       bank). That paper drift is folded into {@link #startNetWorth}
	 *       (mirroring {@link #foldNewlyKnownBankIntoStart}), so the "Net
	 *       worth Δ" reflects session activity rather than price drift on
	 *       cold storage, and the accounting identity
	 *       {@code netWorthDelta == profit - suppliesUsed + unrealizedPnl}
	 *       survives the reload. An out-of-band deposit this engine doesn't
	 *       model (e.g. GE collect-to-bank) is also folded here — the
	 *       matching tracked-wealth movement books to profit/supplies, so the
	 *       identity still holds; only the transfer's label is lost.</li>
	 * </ul>
	 * Anchor-based and idempotent: safe to call from {@link #update},
	 * {@link #snapshot} and {@link #setBankOpen} in any order and repeatedly.
	 * Also expires overdue in-flight expectations. No-op while the bank value
	 * is unknown.
	 *
	 * @return the observed bank movement (0 if none / bank unknown), so a
	 *         caller diffing tracked wealth over the same observation can
	 *         pair the two sides (see {@link #trackOpenTrackedSwing}).
	 */
	private long reconcileBankMovement(WealthSnapshot current, long tsMs)
	{
		expirePendingSettles(tsMs);
		if (!current.isBankKnown())
		{
			return 0L;
		}
		if (bankOpen)
		{
			this.visitSawBank = true;
		}
		if (!bankAnchorKnown)
		{
			this.bankAnchor = current.getBankValue();
			this.bankAnchorKnown = true;
			return 0L;
		}
		long delta = current.getBankValue() - bankAnchor;
		if (delta == 0)
		{
			return 0L;
		}
		this.bankAnchor = current.getBankValue();
		boolean withinGrace = closeGraceArmed
			&& tsMs - lastBankCloseTsMs <= BANK_CLOSE_TRANSFER_GRACE_MS;
		long oldBaseline = this.baseline;
		if (delta > 0)
		{
			// A rise first settles in-flight deposit expectations: the settled
			// portion's baseline fold and expectation consumption cancel in the
			// mark-to-market, so the (already-neutral) deposit stays neutral.
			long settled = consumePendingSettles(delta);
			long transfer = (bankOpen || withinGrace) ? delta : settled;
			long revaluation = delta - transfer;
			this.baseline -= transfer;
			this.startNetWorth += revaluation;
			if (diagEnabled() && (transfer != 0 || revaluation != 0))
			{
				logDiag("[reanchor] bank rise {} (open={} settled={} reval={}): baseline {} -> {}",
					delta, bankOpen, settled, revaluation, oldBaseline, this.baseline);
			}
		}
		else if (bankOpen || withinGrace)
		{
			this.baseline -= delta;
			if (diagEnabled())
			{
				logDiag("[reanchor] bank drop {} (open={} grace={}): transferShift={} baseline {} -> {}",
					delta, bankOpen, withinGrace, -delta, oldBaseline, this.baseline);
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
		return delta;
	}

	/**
	 * Pairs one banking observation's tracked-wealth change against its
	 * bank-side movement (both measured over the same {@link #previous} →
	 * {@code current} interval — callers must advance {@code previous} right
	 * after). A tracked DROP not covered by a same-observation bank rise is
	 * held as an in-flight deposit (see {@link #pendingBankSettles}) rather
	 * than dipping profit; a tracked RISE not explained by a same-observation
	 * bank drop first cancels outstanding expectations (an equip flicker
	 * returning, or an in-flight withdrawal whose expected bank drop nets
	 * against an in-flight deposit's expected rise) before anything counts as
	 * live profit via the mark-to-market. Only meaningful while banking with
	 * a visible bank value — with no bank channel to ever settle against, a
	 * hold would just delay the pre-live blind-visit accounting.
	 */
	private void trackOpenTrackedSwing(WealthSnapshot current, long bankDelta, long tsMs)
	{
		if (previous == null || !current.isBankKnown() || !bankAnchorKnown)
		{
			return;
		}
		long trackedDelta = current.tracked() - previous.tracked();
		if (trackedDelta < 0)
		{
			long uncovered = -trackedDelta - Math.min(-trackedDelta, Math.max(0L, bankDelta));
			if (uncovered > 0)
			{
				pendingBankSettles.add(new PendingBankSettle(uncovered, tsMs));
				if (diagEnabled())
				{
					logDiag("[reanchor] in-flight deposit hold {} (pendingSettle total {})",
						uncovered, pendingSettleTotal());
				}
			}
		}
		else if (trackedDelta > 0)
		{
			long unexplained = trackedDelta - Math.min(trackedDelta, Math.max(0L, -bankDelta));
			if (unexplained > 0)
			{
				long cancelled = consumePendingSettles(unexplained);
				if (cancelled > 0 && diagEnabled())
				{
					logDiag("[reanchor] tracked rise cancels {} of pendingSettle (remaining {})",
						cancelled, pendingSettleTotal());
				}
			}
		}
	}

	/** Sum of the outstanding in-flight deposit expectations. */
	private long pendingSettleTotal()
	{
		long total = 0L;
		for (PendingBankSettle p : pendingBankSettles)
		{
			total += p.amount;
		}
		return total;
	}

	/**
	 * Consumes up to {@code amount} from the in-flight expectations, oldest
	 * first, returning how much was actually consumed.
	 */
	private long consumePendingSettles(long amount)
	{
		long consumed = 0L;
		java.util.Iterator<PendingBankSettle> it = pendingBankSettles.iterator();
		while (it.hasNext() && consumed < amount)
		{
			PendingBankSettle p = it.next();
			long take = Math.min(p.amount, amount - consumed);
			p.amount -= take;
			consumed += take;
			if (p.amount == 0)
			{
				it.remove();
			}
		}
		return consumed;
	}

	/**
	 * Drops in-flight expectations older than
	 * {@link #BANK_TRANSFER_SETTLE_WINDOW_MS}: the bank side never arrived,
	 * so the held tracked drop was a genuine loss and now books as one (the
	 * mark-to-market simply stops adding the expired amount back).
	 */
	private void expirePendingSettles(long tsMs)
	{
		java.util.Iterator<PendingBankSettle> it = pendingBankSettles.iterator();
		while (it.hasNext())
		{
			PendingBankSettle p = it.next();
			if (tsMs - p.tsMs > BANK_TRANSFER_SETTLE_WINDOW_MS)
			{
				if (diagEnabled())
				{
					logDiag("[reanchor] in-flight hold {} expired after {}ms — booking as loss",
						p.amount, tsMs - p.tsMs);
				}
				it.remove();
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
	 * @param geAttributedItemIds item ids attributed to GE activity; every
	 *                            observed quantity swing of such an id is
	 *                            excluded IN FULL from the loot feed and
	 *                            supplies-used tracking. Convenience adapter
	 *                            (tests, simple callers) over the
	 *                            quantity-accurate
	 *                            {@link #update(WealthSnapshot, GeAttributions, long)},
	 *                            which live integration must use instead: an
	 *                            id-only signal cannot distinguish a GE
	 *                            collection from genuine loot of the same
	 *                            item.
	 */
	public void update(WealthSnapshot current, Set<Integer> geAttributedItemIds, long tsMs)
	{
		update(current, asAttributions(geAttributedItemIds), tsMs);
	}

	/**
	 * Adapts an id-only attribution set to the quantity-aware interface:
	 * ids in the set attribute any requested quantity, other ids none —
	 * exactly the historical all-or-nothing exclusion semantics.
	 */
	private static GeAttributions asAttributions(Set<Integer> ids)
	{
		if (ids == null || ids.isEmpty())
		{
			return null;
		}
		return new GeAttributions()
		{
			@Override
			public long attributeArrival(int itemId, long quantity)
			{
				return ids.contains(itemId) ? quantity : 0L;
			}

			@Override
			public long attributeRemoval(int itemId, long quantity)
			{
				return ids.contains(itemId) ? quantity : 0L;
			}
		};
	}

	/**
	 * Advances the session with a new wealth snapshot, attributing per-item
	 * quantity swings to Grand Exchange activity through {@code ge} (see
	 * {@link GeAttributions}): the attributed portion of a swing is a GE
	 * transfer — never loot, never supplies — and only the remainder is
	 * classified. Attribution is quantity-capped by the reconciler's
	 * expectation ledger, which persists across the offer-fill →
	 * inventory-arrival gap (a completed offer can sit in the collection box
	 * indefinitely before the player collects it), so a collection landing
	 * many ticks after its fill is still neutralised while genuine loot of a
	 * GE-traded item id beyond the outstanding expected quantity still
	 * counts. See the class-level docs on {@link #update(WealthSnapshot, Set, long)}
	 * for the full classification rules; {@code ge} may be null (no GE
	 * attribution at all).
	 */
	public void update(WealthSnapshot current, GeAttributions ge, long tsMs)
	{
		if (bankOpen)
		{
			syncCostBasis(current);
			long bankDelta = reconcileBankMovement(current, tsMs);
			trackOpenTrackedSwing(current, bankDelta, tsMs);
			logUpdateBreakdown(current, 0L, 0L, 0L, 0L, 0L, 0L);
			this.previous = current;
			return;
		}

		reconcileBankMovement(current, tsMs);

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
			if (ge != null)
			{
				long attributed = delta > 0
					? ge.attributeArrival(itemId, delta)
					: ge.attributeRemoval(itemId, -delta);
				if (attributed > 0)
				{
					long signedAttributed = delta > 0 ? attributed : -attributed;
					logAttribution(itemId, currentStack.getName(), signedAttributed,
						signedAttributed * currentStack.getUnitValue(),
						"GE(reconciled, excluded from loot/supplies)");
					delta -= signedAttributed;
					if (delta == 0)
					{
						continue;
					}
				}
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
			long vanishedQty = previousStack.getQuantity();
			if (ge != null)
			{
				long attributed = ge.attributeRemoval(itemId, vanishedQty);
				if (attributed > 0)
				{
					logAttribution(itemId, previousStack.getName(), -attributed,
						-attributed * previousStack.getUnitValue(),
						"GE(reconciled, excluded from loot/supplies)");
					vanishedQty -= attributed;
					if (vanishedQty == 0)
					{
						continue;
					}
				}
			}
			if (previousStack.getUnitValue() <= 0)
			{
				logAttribution(itemId, previousStack.getName(), -vanishedQty, 0L,
					"SKIP(no unit value)");
				continue;
			}
			vanished.add(new Swing(itemId, previousStack.getName(), vanishedQty,
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
		reconcileBankMovement(current, tsMs);
		syncCostBasis(current);

		// In-flight deposits (tracked drop observed, lagged bank rise not
		// yet) are still owned wealth: add them back so a transfer is
		// zero-sum even mid-lag. They settle/cancel/expire via
		// reconcileBankMovement / trackOpenTrackedSwing.
		long pendingSettle = pendingSettleTotal();
		long markToMarket = current.tracked() + pendingSettle - baseline;

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
		// Profit/hr extrapolates NET profit — realised gains minus the
		// consumable spend burned to earn them — not gross profit. Supplies
		// are a real cost of the session, so an hour that nets negative after
		// supplies must read negative. (Matches SessionSnapshot#getNetProfit.)
		long netProfit = profit - suppliesUsed;
		long profitPerHour = elapsedMs > 0 ? netProfit * 3600000L / elapsedMs : 0L;
		// In-flight deposits count as owned net worth too — the observed bank
		// value simply hasn't caught up — keeping the accounting identity
		// (netWorthDelta == profit - suppliesUsed + unrealizedPnl) intact
		// during the lag window.
		long netWorthDelta = current.netWorth() + pendingSettle - startNetWorth;

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
					+ "bankOpen={} pendingSettle={} baseline={} m2m={} realised={} unrealized={} netWorthDelta={} "
					+ "| Δrealised={} Δunrealized={} ΔnetWorthDelta={}",
				current.tracked(), current.getInventoryValue(), current.getEquipmentValue(),
				current.getGeInFlightValue(), current.getPouchValue(),
				current.getBankValue(), current.isBankKnown(),
				bankOpen, pendingSettle, baseline, markToMarket, profit, unrealizedPnl, netWorthDelta,
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
