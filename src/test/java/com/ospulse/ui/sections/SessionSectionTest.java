package com.ospulse.ui.sections;

import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;

import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayManager;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for the "Net worth change" reset/toggle interaction:
 * each of the four components (Profit, GE flip, GE positions, Bank) must
 * rebase independently, so resetting the row and then flipping a component's
 * include/exclude toggle (in either order) never produces a phantom jump.
 *
 * <p>Before the fix, {@code resetState}/reset captured ONE aggregate baseline
 * (the raw total at reset time, with whatever components happened to be
 * toggled on then already baked in) and the displayed total was {@code
 * (rawTotal - excludedComponents) - aggregateBaseline}. Subtracting a
 * toggle-filtered total from a baseline captured under a DIFFERENT toggle
 * configuration jumps by exactly the flipped component's value the moment a
 * toggle changes after a reset (or the moment a reset happens while a
 * component is already excluded).
 */
public class SessionSectionTest
{
	private CollapsibleSection.CollapseStore store;
	private SessionSection section;

	@Before
	public void setUp()
	{
		store = new CollapsibleSection.CollapseStore()
		{
			@Override
			public boolean isCollapsed(String key)
			{
				return false;
			}

			@Override
			public void setCollapsed(String key, boolean collapsed)
			{
			}
		};
		Plugin plugin = mock(Plugin.class);
		Client client = mock(Client.class);
		OverlayManager overlayManager = mock(OverlayManager.class);
		section = new SessionSection(store, plugin, client, overlayManager);
	}

	/** Category id for the "Net worth change" row's right-click reset menu. */
	private static final String CAT_NET_WORTH_DELTA = "session:netWorthDelta";

	private static SessionSnapshot snapshotWith(long netProfit, long geRealizedPnl,
		long gePositions, long bankDelta)
	{
		return new SessionSnapshot(
			0L, 1000L, netProfit, 0L, geRealizedPnl, netProfit + geRealizedPnl + gePositions + bankDelta,
			true, Collections.emptyList(), Collections.emptyMap(), 0L, null,
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), 0L, null, 0L, Collections.emptyList(), 0L,
			gePositions, bankDelta);
	}

	@Test
	public void resetThenTogglingGePositionsOffStaysRebasedAtZero()
	{
		// Profit 0, GE flip 0, GE positions +80k (toggle ON, default), Bank 0.
		section.apply(snapshotWith(0L, 0L, 80_000L, 0L));
		assertEquals(80_000L, section.netWorthChangeForTest());

		// Reset the row: with nothing else changing, it must read 0 right after.
		section.resetCategoryForTest(CAT_NET_WORTH_DELTA);
		section.apply(snapshotWith(0L, 0L, 80_000L, 0L));
		assertEquals(0L, section.netWorthChangeForTest());

		// Untick GE positions: the bug subtracted the full 80k from a baseline
		// that already had the 80k baked in, jumping to -80k. It must instead
		// stay at 0 (GE positions' own contribution, raw - itsBaseline, is
		// removed from the total entirely, not double-subtracted).
		section.setGePositionsToggleForTest(false);
		assertEquals("unticking a component right after reset must not jump the total",
			0L, section.netWorthChangeForTest());

		// Re-tick it: back to 0 as well (nothing changed since reset).
		section.setGePositionsToggleForTest(true);
		assertEquals(0L, section.netWorthChangeForTest());
	}

	@Test
	public void togglingBankOffThenResetStaysRebasedAtZeroWhenToggledBackOn()
	{
		// Profit 0, GE flip 0, GE positions 0, Bank +50k.
		section.apply(snapshotWith(0L, 0L, 0L, 50_000L));

		// Untick Bank BEFORE resetting: the displayed total (Profit+GEflip
		// only, Bank excluded) is 0 even though Bank's raw value is +50k.
		section.setBankToggleForTest(false);
		assertEquals(0L, section.netWorthChangeForTest());

		// Reset while Bank is toggled off.
		section.resetCategoryForTest(CAT_NET_WORTH_DELTA);
		section.apply(snapshotWith(0L, 0L, 0L, 50_000L));
		assertEquals(0L, section.netWorthChangeForTest());

		// Re-tick Bank: its own baseline was captured at reset time (50k)
		// regardless of it being excluded then, so it must also read 0 now
		// (raw 50k - baseline 50k), not jump by the full 50k.
		section.setBankToggleForTest(true);
		assertEquals("re-including a component that was excluded at reset time "
				+ "must not jump the total",
			0L, section.netWorthChangeForTest());
	}

	@Test
	public void componentsMoveAfterResetShowOnlyTheDelta()
	{
		section.apply(snapshotWith(0L, 0L, 80_000L, 50_000L));
		section.resetCategoryForTest(CAT_NET_WORTH_DELTA);
		section.apply(snapshotWith(0L, 0L, 80_000L, 50_000L));
		assertEquals(0L, section.netWorthChangeForTest());

		// GE positions rises by 20k, Bank rises by 10k after the reset.
		section.apply(snapshotWith(0L, 0L, 100_000L, 60_000L));
		assertEquals(30_000L, section.netWorthChangeForTest());

		// Toggling Bank off afterwards removes only Bank's OWN delta (10k),
		// leaving GE positions' 20k delta intact.
		section.setBankToggleForTest(false);
		assertEquals(20_000L, section.netWorthChangeForTest());
	}
}
