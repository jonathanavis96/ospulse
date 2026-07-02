package com.phatwang.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ItemStackTest
{
	@Test
	public void valueMultipliesQuantityByUnitValue()
	{
		ItemStack stack = new ItemStack(995, "Coins", 1000L, 1L);
		assertEquals(1000L, stack.value());

		ItemStack stack2 = new ItemStack(11840, "Dragon warhammer", 3L, 40_000_000L);
		assertEquals(120_000_000L, stack2.value());
	}

	@Test
	public void valueHandlesZeroQuantity()
	{
		ItemStack stack = new ItemStack(1, "Nothing", 0L, 500L);
		assertEquals(0L, stack.value());
	}

	@Test
	public void gettersReturnConstructorArgs()
	{
		ItemStack stack = new ItemStack(4151, "Abyssal whip", 1L, 2_000_000L);
		assertEquals(4151, stack.getId());
		assertEquals("Abyssal whip", stack.getName());
		assertEquals(1L, stack.getQuantity());
		assertEquals(2_000_000L, stack.getUnitValue());
	}

	@Test
	public void equalsAndHashCodeAreValueBased()
	{
		ItemStack a = new ItemStack(1, "Rune scimitar", 1L, 15_000L);
		ItemStack b = new ItemStack(1, "Rune scimitar", 1L, 15_000L);
		ItemStack c = new ItemStack(2, "Rune scimitar", 1L, 15_000L);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
	}

	@Test
	public void toStringContainsFields()
	{
		ItemStack stack = new ItemStack(995, "Coins", 100L, 1L);
		String s = stack.toString();
		assertTrue(s.contains("995"));
		assertTrue(s.contains("Coins"));
	}
}
