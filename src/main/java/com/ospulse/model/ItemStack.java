package com.ospulse.model;

import java.util.Objects;

/**
 * Immutable value type representing a quantity of a single item at a given
 * per-unit value. Pure domain type: no RuneLite imports, unit-testable
 * without a game client.
 */
public final class ItemStack
{
	private final int id;
	private final String name;
	private final long quantity;
	private final long unitValue;
	private final long haPrice;

	public ItemStack(int id, String name, long quantity, long unitValue)
	{
		this(id, name, quantity, unitValue, -1L);
	}

	/**
	 * @param haPrice per-unit High Alchemy price, or {@code -1} if not resolved
	 *                (e.g. no {@code ItemComposition} was available). Must be
	 *                resolved on the RuneLite client thread — never the Swing
	 *                EDT, since {@code ItemManager.getItemComposition} asserts
	 *                that thread.
	 */
	public ItemStack(int id, String name, long quantity, long unitValue, long haPrice)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.unitValue = unitValue;
		this.haPrice = haPrice;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public long getQuantity()
	{
		return quantity;
	}

	public long getUnitValue()
	{
		return unitValue;
	}

	/**
	 * Per-unit High Alchemy price, resolved on the client thread at
	 * construction time; {@code -1} if not resolved (composition unavailable).
	 */
	public long getHaPrice()
	{
		return haPrice;
	}

	/**
	 * Total value of this stack: quantity * unitValue.
	 */
	public long value()
	{
		return quantity * unitValue;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof ItemStack))
		{
			return false;
		}
		ItemStack itemStack = (ItemStack) o;
		return id == itemStack.id
			&& quantity == itemStack.quantity
			&& unitValue == itemStack.unitValue
			&& Objects.equals(name, itemStack.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, quantity, unitValue);
	}

	@Override
	public String toString()
	{
		return "ItemStack{"
			+ "id=" + id
			+ ", name='" + name + '\''
			+ ", quantity=" + quantity
			+ ", unitValue=" + unitValue
			+ '}';
	}
}
