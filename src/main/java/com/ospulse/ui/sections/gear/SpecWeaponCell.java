package com.ospulse.ui.sections.gear;

import com.ospulse.combat.SpecWeaponRecommendation;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Cursor;
import java.awt.Dimension;

/**
 * The "best spec weapon" cell in the Gear section's equipment grid (design
 * spec §8 — placed at the unused flat filler slot directly below WEAPON and
 * above GLOVES; see {@code GearSection}'s {@code SLOT_GRID}).
 *
 * <p>Deliberately its OWN small class with its OWN {@link JLabel} and its OWN
 * diff-guard ({@link #renderedItemId}), entirely separate from {@code
 * GearSection}'s {@code slotLabels[]}/{@code renderedSlotIds[]} arrays: this
 * is a pseudo-slot with no {@code EquipmentInventorySlot} ordinal, and those
 * two arrays are sized and indexed by the 14 REAL ordinals
 * ({@code GearSnapshot.EQUIPMENT_SLOT_COUNT}) — squatting on an unused
 * ordinal there would silently break whichever real slot happened to claim
 * it next. Parallel plumbing, not shared plumbing.
 */
public final class SpecWeaponCell extends JLabel {
    /** Diff-guard, mirroring {@code GearSection.renderedSlotIds}' role for the real slots: avoids re-fetching the same sprite every refresh. */
    private int renderedItemId = Integer.MIN_VALUE;

    public SpecWeaponCell(int slotWidth, int slotHeight) {
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        setPreferredSize(new Dimension(slotWidth, slotHeight));
        setCursor(Cursor.getDefaultCursor());
        setToolTipText(noTargetTooltip());
    }

    /**
     * Rebuilds this cell for {@code recommendation} ({@code null} = no target
     * selected, or nothing owned+legal qualifies — see {@code
     * SpecWeaponSelector}). {@code itemManager} may be {@code null} in
     * headless tests, in which case the icon is skipped but the tooltip still
     * updates (mirrors {@code GearSection.updateGearGrid}'s own null-safety).
     */
    public void refresh(SpecWeaponRecommendation recommendation, ItemManager itemManager) {
        int id = recommendation == null ? -1 : recommendation.itemId();
        setToolTipText(recommendation == null ? noTargetTooltip() : recommendation.readoutText());
        if (id == renderedItemId) {
            return;
        }
        renderedItemId = id;
        if (id > 0 && itemManager != null) {
            // EDT-safe: getImage is async (AsyncBufferedImage), the ONLY
            // ItemManager call permitted off the client thread — same as
            // every other slot cell in GearSection.
            itemManager.getImage(id).addTo(this);
        } else {
            setIcon(null);
        }
    }

    private static String noTargetTooltip() {
        return "Best spec weapon — pick a target to see a recommendation";
    }

    /** Test seam: the item id this cell last rendered (or {@code -1}/{@code Integer.MIN_VALUE} before any refresh). */
    public int renderedItemIdForTest() {
        return renderedItemId;
    }
}
