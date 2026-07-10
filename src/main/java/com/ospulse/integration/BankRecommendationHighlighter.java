package com.ospulse.integration;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Filters the open bank down to a set of item ids using RuneLite's Bank Tags
 * system (same mechanism as the Inventory Setups plugin). Owns a single
 * reserved hidden tag so the user's own tags are never touched.
 */
public class BankRecommendationHighlighter
{
    public static final String RESERVED_TAG = "ospulse-recommended";

    private static final int OPEN_OPTIONS =
        BankTagsService.OPTION_HIDE_TAG_NAME | BankTagsService.OPTION_NO_LAYOUT;

    private final BankTagsService bankTags;   // nullable
    private final TagManager tagManager;      // nullable
    private final ClientThread clientThread;

    private final Set<Integer> current = new LinkedHashSet<>();
    private boolean armed;

    public BankRecommendationHighlighter(BankTagsService bankTags, TagManager tagManager,
                                         ClientThread clientThread)
    {
        this.bankTags = bankTags;
        this.tagManager = tagManager;
        this.clientThread = clientThread;
    }

    public boolean isArmed()
    {
        return armed;
    }

    public void showInBank(Set<Integer> itemIds)
    {
        if (bankTags == null || tagManager == null || clientThread == null || itemIds == null)
        {
            return;
        }
        current.clear();
        for (Integer id : itemIds)
        {
            if (id != null && id > 0)
            {
                current.add(id);
            }
        }
        armed = true;
        clientThread.invoke(this::applyReserved);
    }

    public void reapplyIfArmed()
    {
        if (armed && bankTags != null && clientThread != null)
        {
            clientThread.invoke(this::applyReserved);
        }
    }

    public void clear()
    {
        armed = false;
        current.clear();
        if (tagManager == null || bankTags == null || clientThread == null)
        {
            return;
        }
        clientThread.invoke(() ->
        {
            tagManager.removeTag(RESERVED_TAG);
            bankTags.closeBankTag();
        });
    }

    private void applyReserved()
    {
        tagManager.removeTag(RESERVED_TAG);              // reset — never accumulate
        tagManager.setHidden(RESERVED_TAG, true);
        for (int id : current)
        {
            tagManager.addTag(id, RESERVED_TAG, false);
        }
        bankTags.openBankTag(RESERVED_TAG, OPEN_OPTIONS);
    }
}
