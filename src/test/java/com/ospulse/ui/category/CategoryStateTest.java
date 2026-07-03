package com.ospulse.ui.category;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CategoryStateTest
{
	@Test
	public void startsNotPausedWithEpochZero()
	{
		CategoryState state = new CategoryState("loot:Cerberus");

		assertEquals("loot:Cerberus", state.getId());
		assertFalse(state.isPaused());
		assertEquals(0, state.getResetEpoch());
		assertEquals(0L, state.getResetAtMs());
	}

	@Test
	public void togglePausedFlips()
	{
		CategoryState state = new CategoryState("x");

		state.togglePaused();
		assertTrue(state.isPaused());

		state.togglePaused();
		assertFalse(state.isPaused());
	}

	@Test
	public void resetIncrementsEpochAndRecordsTimestamp()
	{
		CategoryState state = new CategoryState("x");

		state.reset(1234L);
		assertEquals(1, state.getResetEpoch());
		assertEquals(1234L, state.getResetAtMs());

		state.reset(5678L);
		assertEquals(2, state.getResetEpoch());
		assertEquals(5678L, state.getResetAtMs());
	}
}
