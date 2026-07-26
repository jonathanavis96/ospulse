package com.ospulse.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A per-item-id FIFO of not-yet-matched receipted quantity, aging toward
 * expiry — the shared cross-tick netting seam behind two independent
 * consumers that both need exactly the same guarantee.
 *
 * <p><b>Why this exists.</b> RuneLite's {@code LootReceived} for an ordinary
 * NPC kill fires as soon as the loot is known — at the NPC's death — not when
 * the player later walks over and picks it up; the inventory diff for that
 * pickup can land several ticks after the receipt (confirmed by decompiling
 * {@code client-1.12.33.jar}: the event is raised from the death/loot-roll
 * code path, never from the ground-item pickup path). A receipt that isn't
 * matched on its own tick is therefore not stale; it stays eligible to net
 * against the diff whenever the pickup actually happens, up to the
 * configured window. Past that window it is dropped: either it was never
 * picked up (looted by someone else, despawned) or matching it this late
 * would risk swallowing a genuinely new, unrelated appearance of the same id.
 *
 * <p>Two independent consumers need exactly this:
 * <ul>
 *   <li>{@code com.ospulse.integration.SessionTracker}'s loot feed: a
 *       receipted kill must not double-count as {@code Inventory
 *       (unattributed)} when its pickup is only observed on a later tick.</li>
 *   <li>{@link SessionEngine}'s episode ledger: an unvouched appearance
 *       during an open production episode is credited as manufactured output
 *       only up to the value of inputs actually charged — a receipted
 *       appearance must still be recognised as genuine loot (not manufactured
 *       output) even when the receipt fired several ticks before the pickup
 *       landed.</li>
 * </ul>
 *
 * <p>Each consumer owns its own instance — they track different quantities
 * for different purposes and clear at different points in their own
 * lifecycle — but the FIFO netting and age-based expiry are identical, so
 * that logic lives once here rather than being maintained twice.
 *
 * <p>Mutable, single-threaded use only. Not thread-safe.
 */
public final class OutstandingReceiptLedger
{
	/** One not-yet-matched slice of a receipt, aging toward expiry. */
	private static final class Entry
	{
		long quantity;
		final long tsMs;

		Entry(long quantity, long tsMs)
		{
			this.quantity = quantity;
			this.tsMs = tsMs;
		}
	}

	private final long windowMs;
	private final Map<Integer, Deque<Entry>> pool = new HashMap<>();

	/**
	 * @param windowMs how long an entry stays eligible to net against a later
	 *                  claim before {@link #pruneExpired} drops it as stale.
	 */
	public OutstandingReceiptLedger(long windowMs)
	{
		this.windowMs = windowMs;
	}

	/** Records {@code quantity} of {@code itemId} as an outstanding, not-yet-matched receipt. */
	public void record(int itemId, long quantity, long tsMs)
	{
		if (quantity <= 0)
		{
			return;
		}
		pool.computeIfAbsent(itemId, k -> new ArrayDeque<>()).addLast(new Entry(quantity, tsMs));
	}

	/**
	 * Nets {@code quantity} of {@code itemId} against the oldest outstanding
	 * entries first (FIFO), consuming/removing them as they're used up.
	 * Returns whatever quantity is left unmatched — the genuine, never-
	 * receipted remainder. Does NOT prune by age itself; call {@link
	 * #pruneExpired} first (once per tick/update, not once per item, for
	 * efficiency) so an expired entry is never netted against.
	 */
	public long claim(int itemId, long quantity)
	{
		Deque<Entry> pending = pool.get(itemId);
		if (pending == null)
		{
			return quantity;
		}
		long remaining = quantity;
		while (remaining > 0 && !pending.isEmpty())
		{
			Entry head = pending.peekFirst();
			long netted = Math.min(remaining, head.quantity);
			head.quantity -= netted;
			remaining -= netted;
			if (head.quantity <= 0)
			{
				pending.pollFirst();
			}
		}
		if (pending.isEmpty())
		{
			pool.remove(itemId);
		}
		return remaining;
	}

	/**
	 * Drops every entry older than the configured window (across all ids) so
	 * an item that was never picked up cannot sit around indefinitely and
	 * wrongly net out an unrelated later find.
	 */
	public void pruneExpired(long tsMs)
	{
		Iterator<Map.Entry<Integer, Deque<Entry>>> it = pool.entrySet().iterator();
		while (it.hasNext())
		{
			Deque<Entry> pending = it.next().getValue();
			while (!pending.isEmpty() && tsMs - pending.peekFirst().tsMs > windowMs)
			{
				pending.pollFirst();
			}
			if (pending.isEmpty())
			{
				it.remove();
			}
		}
	}

	/** Clears all state — for a new session/login, so a stale entry never survives into a fresh one. */
	public void clear()
	{
		pool.clear();
	}
}
