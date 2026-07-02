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

	public ItemStack(int id, String name, long quantity, long unitValue)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.unitValue = unitValue;
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
