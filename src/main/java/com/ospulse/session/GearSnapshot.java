package com.ospulse.session;

import com.ospulse.combat.EquipmentStats;
import com.ospulse.combat.OffensivePrayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable, live snapshot of the player's combat-relevant state: worn gear
 * (by item id, one slot per {@code net.runelite.api.EquipmentInventorySlot}
 * ordinal) plus base/boosted skill levels and active offensive prayers.
 *
 * <p>Deliberately has zero RuneLite dependency — {@code com.ospulse.integration.SessionTracker}
 * (on the client thread) is the only place that reads the live {@code Client}
 * and builds one of these, including resolving {@link #equipmentStats()} via
 * {@code ItemManager.getItemStats} <b>on the client thread</b> (that call
 * internally asserts the client thread and must never be made from the EDT);
 * {@code com.ospulse.ui.sections.GearSection} (on the EDT) only ever reads
 * the precomputed fields of this snapshot to drive the
 * {@code com.ospulse.combat} DPS engine. This keeps the gear-&gt;DPS mapping
 * pure and unit-testable without a running game client.
 */
public final class GearSnapshot
{
	/** {@code net.runelite.api.EquipmentInventorySlot.values().length} — kept in sync manually since this class has no RuneLite dependency. */
	public static final int EQUIPMENT_SLOT_COUNT = 14;

	private static final GearSnapshot EMPTY = builder().build();

	private final int[] equippedItemIds;
	private final int baseAttack;
	private final int boostedAttack;
	private final int baseStrength;
	private final int boostedStrength;
	private final int baseDefence;
	private final int boostedDefence;
	private final int baseRanged;
	private final int boostedRanged;
	private final int baseMagic;
	private final int boostedMagic;
	private final int basePrayer;
	private final int boostedPrayer;
	private final int baseHitpoints;
	private final int boostedHitpoints;
	private final int baseSlayer;
	private final int boostedSlayer;
	private final int baseAgility;
	private final int boostedAgility;
	private final Set<OffensivePrayer> activePrayers;
	/**
	 * TODO Phase 2+: on-task Slayer detection is not wired to a live client
	 * read yet (no confirmed API in the design brief's integration section);
	 * always {@code false} for now, so Slayer helm(i)/black mask(i) on-task
	 * bonuses never apply until this is read live.
	 */
	private final boolean onSlayerTask;
	/**
	 * Pre-summed loadout-wide {@link EquipmentStats}, resolved once on the
	 * client thread by {@code SessionTracker.buildGear()} (via {@code
	 * GearMapper.buildEquipmentStats} + {@code ItemManager.getItemStats}) —
	 * never on the EDT. {@code null} for {@link #empty()} / whenever no live
	 * gear has been resolved yet.
	 */
	private final EquipmentStats equipmentStats;
	/**
	 * Raw value of the in-game autocast-spell varbit ({@code
	 * gameval.VarbitID.AUTOCAST_SPELL}, id 276): the spell the player has set to
	 * auto-cast, as the game's internal autocast id. This is NOT one of our
	 * {@link com.ospulse.combat.Spell} ids — the value-&gt;spell mapping is cache
	 * data that must be captured in-client (see {@code SessionTracker.buildGear}'s
	 * debug log) before the magic readout can resolve it. {@code -1} (or {@code 0})
	 * means no autocast spell is set. Exposed so the {@code GearSection} secondary
	 * readout can eventually show the actual current cast instead of the
	 * next-best-DPS fallback.
	 */
	private final int autocastSpellId;

	private GearSnapshot(Builder b)
	{
		this.equippedItemIds = normalizeSlots(b.equippedItemIds);
		this.baseAttack = b.baseAttack;
		this.boostedAttack = b.boostedAttack;
		this.baseStrength = b.baseStrength;
		this.boostedStrength = b.boostedStrength;
		this.baseDefence = b.baseDefence;
		this.boostedDefence = b.boostedDefence;
		this.baseRanged = b.baseRanged;
		this.boostedRanged = b.boostedRanged;
		this.baseMagic = b.baseMagic;
		this.boostedMagic = b.boostedMagic;
		this.basePrayer = b.basePrayer;
		this.boostedPrayer = b.boostedPrayer;
		this.baseHitpoints = b.baseHitpoints;
		this.boostedHitpoints = b.boostedHitpoints;
		this.baseSlayer = b.baseSlayer;
		this.boostedSlayer = b.boostedSlayer;
		this.baseAgility = b.baseAgility;
		this.boostedAgility = b.boostedAgility;
		this.activePrayers = b.activePrayers.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(EnumSet.copyOf(b.activePrayers));
		this.onSlayerTask = b.onSlayerTask;
		this.equipmentStats = b.equipmentStats;
		this.autocastSpellId = b.autocastSpellId;
	}

	private static int[] normalizeSlots(int[] raw)
	{
		if (raw == null)
		{
			int[] empty = new int[EQUIPMENT_SLOT_COUNT];
			Arrays.fill(empty, -1);
			return empty;
		}
		return Arrays.copyOf(raw, raw.length);
	}

	/** Shared empty instance: no gear, all levels 0, no active prayers. Used as the default before the first live snapshot. */
	public static GearSnapshot empty()
	{
		return EMPTY;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	/**
	 * Worn item id per {@code EquipmentInventorySlot} ordinal (0=HEAD .. 13=AMMO);
	 * {@code -1} (or {@code 0}) means the slot is empty.
	 */
	public int[] equippedItemIds()
	{
		return Arrays.copyOf(equippedItemIds, equippedItemIds.length);
	}

	public int itemIdAt(int slotOrdinal)
	{
		return slotOrdinal >= 0 && slotOrdinal < equippedItemIds.length ? equippedItemIds[slotOrdinal] : -1;
	}

	public int baseAttack()
	{
		return baseAttack;
	}

	public int boostedAttack()
	{
		return boostedAttack;
	}

	public int baseStrength()
	{
		return baseStrength;
	}

	public int boostedStrength()
	{
		return boostedStrength;
	}

	public int baseDefence()
	{
		return baseDefence;
	}

	public int boostedDefence()
	{
		return boostedDefence;
	}

	public int baseRanged()
	{
		return baseRanged;
	}

	public int boostedRanged()
	{
		return boostedRanged;
	}

	public int baseMagic()
	{
		return baseMagic;
	}

	public int boostedMagic()
	{
		return boostedMagic;
	}

	public int basePrayer()
	{
		return basePrayer;
	}

	public int boostedPrayer()
	{
		return boostedPrayer;
	}

	public int baseHitpoints()
	{
		return baseHitpoints;
	}

	public int boostedHitpoints()
	{
		return boostedHitpoints;
	}

	public int baseSlayer()
	{
		return baseSlayer;
	}

	public int boostedSlayer()
	{
		return boostedSlayer;
	}

	public int baseAgility()
	{
		return baseAgility;
	}

	public int boostedAgility()
	{
		return boostedAgility;
	}

	public Set<OffensivePrayer> activePrayers()
	{
		return activePrayers;
	}

	public boolean onSlayerTask()
	{
		return onSlayerTask;
	}

	/**
	 * Pre-summed loadout-wide equipment bonuses, or {@code null} if not yet
	 * resolved (e.g. {@link #empty()}). Safe to read on any thread, including
	 * the EDT — all the client-thread-only resolution already happened when
	 * this snapshot was built.
	 */
	public EquipmentStats equipmentStats()
	{
		return equipmentStats;
	}

	/**
	 * Raw in-game autocast-spell varbit value (id 276); {@code -1}/{@code 0} =
	 * none set. See the field javadoc — this is the game's internal autocast id,
	 * not a {@link com.ospulse.combat.Spell} id, and needs an in-client-captured
	 * mapping before it can be resolved to a named spell.
	 */
	public int autocastSpellId()
	{
		return autocastSpellId;
	}

	public static final class Builder
	{
		private int[] equippedItemIds;
		private int baseAttack;
		private int boostedAttack;
		private int baseStrength;
		private int boostedStrength;
		private int baseDefence;
		private int boostedDefence;
		private int baseRanged;
		private int boostedRanged;
		private int baseMagic;
		private int boostedMagic;
		private int basePrayer;
		private int boostedPrayer;
		private int baseHitpoints;
		private int boostedHitpoints;
		private int baseSlayer;
		private int boostedSlayer;
		private int baseAgility;
		private int boostedAgility;
		private Set<OffensivePrayer> activePrayers = EnumSet.noneOf(OffensivePrayer.class);
		private boolean onSlayerTask;
		private EquipmentStats equipmentStats;
		private int autocastSpellId = -1;

		private Builder()
		{
		}

		public Builder equippedItemIds(int[] ids)
		{
			this.equippedItemIds = ids;
			return this;
		}

		public Builder attack(int base, int boosted)
		{
			this.baseAttack = base;
			this.boostedAttack = boosted;
			return this;
		}

		public Builder strength(int base, int boosted)
		{
			this.baseStrength = base;
			this.boostedStrength = boosted;
			return this;
		}

		public Builder defence(int base, int boosted)
		{
			this.baseDefence = base;
			this.boostedDefence = boosted;
			return this;
		}

		public Builder ranged(int base, int boosted)
		{
			this.baseRanged = base;
			this.boostedRanged = boosted;
			return this;
		}

		public Builder magic(int base, int boosted)
		{
			this.baseMagic = base;
			this.boostedMagic = boosted;
			return this;
		}

		public Builder prayer(int base, int boosted)
		{
			this.basePrayer = base;
			this.boostedPrayer = boosted;
			return this;
		}

		public Builder hitpoints(int base, int boosted)
		{
			this.baseHitpoints = base;
			this.boostedHitpoints = boosted;
			return this;
		}

		public Builder slayer(int base, int boosted)
		{
			this.baseSlayer = base;
			this.boostedSlayer = boosted;
			return this;
		}

		public Builder agility(int base, int boosted)
		{
			this.baseAgility = base;
			this.boostedAgility = boosted;
			return this;
		}

		public Builder activePrayers(Set<OffensivePrayer> prayers)
		{
			this.activePrayers = (prayers == null || prayers.isEmpty())
				? EnumSet.noneOf(OffensivePrayer.class)
				: EnumSet.copyOf(prayers);
			return this;
		}

		public Builder onSlayerTask(boolean value)
		{
			this.onSlayerTask = value;
			return this;
		}

		public Builder equipmentStats(EquipmentStats equipmentStats)
		{
			this.equipmentStats = equipmentStats;
			return this;
		}

		/** Raw autocast-spell varbit value (id 276); {@code -1}/{@code 0} = none. */
		public Builder autocastSpellId(int id)
		{
			this.autocastSpellId = id;
			return this;
		}

		public GearSnapshot build()
		{
			return new GearSnapshot(this);
		}
	}
}
