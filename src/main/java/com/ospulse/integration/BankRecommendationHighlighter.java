package com.ospulse.integration;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.util.Text;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters the open bank down to a set of recommended items using RuneLite's
 * Bank Tags system (same mechanism as the Inventory Setups plugin), arranged in
 * the in-game equipment grid via a Bank Tags <em>layout</em>. Owns a single
 * reserved hidden tag + its layout so the user's own tags are never touched.
 */
public class BankRecommendationHighlighter
{
    public static final String RESERVED_TAG = "ospulse-recommended";

    private static final String BANKTAGS_GROUP = "banktags";
    private static final String LAYOUT_KEY = "layout_" + Text.standardize(RESERVED_TAG);

    // NO_LAYOUT is deliberately absent so openBankTag renders our equipment grid.
    private static final int OPEN_OPTIONS = BankTagsService.OPTION_HIDE_TAG_NAME;

    /** Bank grid width (items per row) that the Bank Tags layout array is indexed against. */
    private static final int BANK_COLS = 8;
    /** Flat layout array length — index 34 (ring) is the last equipment cell. */
    private static final int LAYOUT_SIZE = 35;

    /**
     * Equipment slot ordinal -> flat bank-grid index (row*8 + col), mirroring the
     * in-game equipment layout Inventory Setups uses:
     * row1 col2 helmet; row2 cape/amulet/ammo; row3 weapon/chest/shield;
     * row4 legs; row5 gloves/boots/ring.
     */
    private static final Map<Integer, Integer> SLOT_TO_GRID = new LinkedHashMap<>();
    static
    {
        SLOT_TO_GRID.put(0, 0 * BANK_COLS + 1);   // HEAD   -> (1,2) = 1
        SLOT_TO_GRID.put(1, 1 * BANK_COLS + 0);   // CAPE   -> (2,1) = 8
        SLOT_TO_GRID.put(2, 1 * BANK_COLS + 1);   // AMULET -> (2,2) = 9
        SLOT_TO_GRID.put(13, 1 * BANK_COLS + 2);  // AMMO   -> (2,3) = 10
        SLOT_TO_GRID.put(3, 2 * BANK_COLS + 0);   // WEAPON -> (3,1) = 16
        SLOT_TO_GRID.put(4, 2 * BANK_COLS + 1);   // BODY   -> (3,2) = 17
        SLOT_TO_GRID.put(5, 2 * BANK_COLS + 2);   // SHIELD -> (3,3) = 18
        SLOT_TO_GRID.put(7, 3 * BANK_COLS + 1);   // LEGS   -> (4,2) = 25
        SLOT_TO_GRID.put(9, 4 * BANK_COLS + 0);   // GLOVES -> (5,1) = 32
        SLOT_TO_GRID.put(10, 4 * BANK_COLS + 1);  // BOOTS  -> (5,2) = 33
        SLOT_TO_GRID.put(12, 4 * BANK_COLS + 2);  // RING   -> (5,3) = 34
    }

    private final BankTagsService bankTags;      // nullable
    private final TagManager tagManager;         // nullable
    private final ConfigManager configManager;   // nullable
    private final ClientThread clientThread;

    // slot ordinal -> item id. Written on the EDT, read on the client thread.
    private Map<Integer, Integer> current = new LinkedHashMap<>();
    // Written on the EDT (showInBank/clear), read on the client thread (reapplyIfArmed).
    private volatile boolean armed;

    public BankRecommendationHighlighter(BankTagsService bankTags, TagManager tagManager,
                                         ConfigManager configManager, ClientThread clientThread)
    {
        this.bankTags = bankTags;
        this.tagManager = tagManager;
        this.configManager = configManager;
        this.clientThread = clientThread;
    }

    public boolean isArmed()
    {
        return armed;
    }

    /**
     * Show the given recommended items, keyed by equipment slot ordinal so they
     * can be laid out in the equipment grid.
     */
    public void showInBank(Map<Integer, Integer> slotToItemId)
    {
        if (bankTags == null || tagManager == null || configManager == null
            || clientThread == null || slotToItemId == null)
        {
            return;
        }
        Map<Integer, Integer> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : slotToItemId.entrySet())
        {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0)
            {
                snapshot.put(e.getKey(), e.getValue());
            }
        }
        armed = true;
        clientThread.invoke(() ->
        {
            current = snapshot;
            applyReserved();
        });
    }

    /** Re-open the filtered grid if armed (bank re-open, or OSPulse panel re-activated). */
    public void reapplyIfArmed()
    {
        if (armed && bankTags != null && tagManager != null && configManager != null && clientThread != null)
        {
            clientThread.invoke(this::applyReserved);
        }
    }

    /**
     * Hide the filtered view WITHOUT disarming, so re-activating the OSPulse
     * panel (or re-opening the bank) restores it — used when the user switches
     * to another side-panel plugin (e.g. Inventory Setups).
     */
    public void suspend()
    {
        if (armed && bankTags != null && clientThread != null)
        {
            clientThread.invoke(bankTags::closeBankTag);
        }
    }

    public void clear()
    {
        armed = false;
        if (tagManager == null || bankTags == null || configManager == null || clientThread == null)
        {
            return;
        }
        clientThread.invoke(() ->
        {
            current = new LinkedHashMap<>();
            tagManager.removeTag(RESERVED_TAG);
            configManager.unsetConfiguration(BANKTAGS_GROUP, LAYOUT_KEY);
            bankTags.closeBankTag();
        });
    }

    private void applyReserved()
    {
        tagManager.removeTag(RESERVED_TAG);              // reset — never accumulate
        tagManager.setHidden(RESERVED_TAG, true);
        for (int id : current.values())
        {
            tagManager.addTag(id, RESERVED_TAG, false);
        }
        writeLayout();
        bankTags.openBankTag(RESERVED_TAG, OPEN_OPTIONS);
    }

    /** Persist the equipment-grid layout as the banktags {@code layout_<tag>} CSV the client auto-loads. */
    private void writeLayout()
    {
        int[] grid = new int[LAYOUT_SIZE];
        Arrays.fill(grid, -1);                            // -1 = empty cell
        for (Map.Entry<Integer, Integer> e : current.entrySet())
        {
            Integer idx = SLOT_TO_GRID.get(e.getKey());
            if (idx != null && idx >= 0 && idx < grid.length)
            {
                grid[idx] = e.getValue();
            }
        }
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < grid.length; i++)
        {
            if (i > 0)
            {
                csv.append(',');
            }
            csv.append(grid[i]);
        }
        configManager.setConfiguration(BANKTAGS_GROUP, LAYOUT_KEY, csv.toString());
    }
}
