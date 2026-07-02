package com.ospulse.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LootEntryTest
{
	@Test
	public void gettersReturnConstructorArgs()
	{
		LootEntry entry = new LootEntry(536, "Dragon bones", 100L, 1_000_000L, 12345L);
		assertEquals(536, entry.getItemId());
		assertEquals("Dragon bones", entry.getName());
		assertEquals(100L, entry.getQuantity());
		assertEquals(1_000_000L, entry.getValue());
		assertEquals(12345L, entry.getTimestampMs());
	}

	@Test
	public void equalsAndHashCodeAreValueBased()
	{
		LootEntry a = new LootEntry(536, "Dragon bones", 100L, 1_000_000L, 12345L);
		LootEntry b = new LootEntry(536, "Dragon bones", 100L, 1_000_000L, 12345L);
		LootEntry c = new LootEntry(536, "Dragon bones", 99L, 1_000_000L, 12345L);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
	}
}
