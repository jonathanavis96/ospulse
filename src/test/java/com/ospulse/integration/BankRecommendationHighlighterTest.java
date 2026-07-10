package com.ospulse.integration;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BankRecommendationHighlighterTest
{
    private BankTagsService tags;
    private TagManager tagManager;
    private ConfigManager configManager;
    private ClientThread clientThread;
    private BankRecommendationHighlighter highlighter;

    /** A single-entry slot-ordinal -> item-id loadout. */
    private static Map<Integer, Integer> slot(int slotOrdinal, int itemId)
    {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        m.put(slotOrdinal, itemId);
        return m;
    }

    @Before
    public void setUp()
    {
        tags = mock(BankTagsService.class);
        tagManager = mock(TagManager.class);
        configManager = mock(ConfigManager.class);
        clientThread = mock(ClientThread.class);
        // Run any client-thread runnable synchronously.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
            .when(clientThread).invoke(any(Runnable.class));
        highlighter = new BankRecommendationHighlighter(tags, tagManager, configManager, clientThread);
    }

    @Test
    public void showInBankTagsIdsAndOpensTag()
    {
        Map<Integer, Integer> loadout = new LinkedHashMap<>();
        loadout.put(3, 4151);   // weapon
        loadout.put(0, 11802);  // head
        highlighter.showInBank(loadout);

        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(4151, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tagManager).addTag(11802, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tags).openBankTag(eq(BankRecommendationHighlighter.RESERVED_TAG), anyInt());
        assertTrue(highlighter.isArmed());
    }

    @Test
    public void writesEquipmentGridLayoutCsv()
    {
        Map<Integer, Integer> loadout = new LinkedHashMap<>();
        loadout.put(3, 4151);    // weapon -> grid idx 16
        loadout.put(0, 11802);   // head   -> grid idx 1
        highlighter.showInBank(loadout);

        ArgumentCaptor<String> csv = ArgumentCaptor.forClass(String.class);
        verify(configManager).setConfiguration(eq("banktags"), startsWith("layout_"), csv.capture());
        String[] cells = csv.getValue().split(",", -1);
        assertEquals(35, cells.length);
        assertEquals("11802", cells[1]);   // head at (1,2)
        assertEquals("4151", cells[16]);   // weapon at (3,1)
        assertEquals("-1", cells[0]);      // empty cell sentinel
    }

    @Test
    public void openTagDoesNotSuppressLayout()
    {
        highlighter.showInBank(slot(3, 4151));
        ArgumentCaptor<Integer> opts = ArgumentCaptor.forClass(Integer.class);
        verify(tags).openBankTag(eq(BankRecommendationHighlighter.RESERVED_TAG), opts.capture());
        assertEquals("layout must render (OPTION_NO_LAYOUT must NOT be set)",
            0, opts.getValue() & BankTagsService.OPTION_NO_LAYOUT);
    }

    @Test
    public void clearRemovesTagLayoutAndCloses()
    {
        highlighter.showInBank(slot(3, 4151));
        reset(tagManager, tags, configManager);
        highlighter.clear();
        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(configManager).unsetConfiguration(eq("banktags"), startsWith("layout_"));
        verify(tags).closeBankTag();
        assertFalse(highlighter.isArmed());
    }

    @Test
    public void suspendClosesButStaysArmed()
    {
        highlighter.showInBank(slot(3, 4151));
        reset(tagManager, tags, configManager);
        highlighter.suspend();
        verify(tags).closeBankTag();
        assertTrue("suspend must not disarm — the panel can re-activate later", highlighter.isArmed());
        verifyNoInteractions(tagManager);
    }

    @Test
    public void reShowReplacesRatherThanAccumulates()
    {
        highlighter.showInBank(slot(3, 4151));
        highlighter.showInBank(slot(3, 11802));
        verify(tagManager, times(2)).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(11802, BankRecommendationHighlighter.RESERVED_TAG, false);
    }

    @Test
    public void reapplyIfArmedReappliesReservedTagWhenArmed()
    {
        highlighter.showInBank(slot(3, 4151));
        reset(tagManager, tags, configManager);

        highlighter.reapplyIfArmed();

        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(4151, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tags).openBankTag(eq(BankRecommendationHighlighter.RESERVED_TAG), anyInt());
    }

    @Test
    public void reapplyIfArmedNoOpWhenNotArmed()
    {
        highlighter.reapplyIfArmed();
        verifyNoInteractions(tagManager, tags);

        highlighter.showInBank(slot(3, 4151));
        highlighter.clear();
        reset(tagManager, tags, configManager);
        highlighter.reapplyIfArmed();
        verifyNoInteractions(tagManager, tags);
    }

    @Test
    public void nullServicesNoOp()
    {
        BankRecommendationHighlighter h = new BankRecommendationHighlighter(null, null, null, clientThread);
        h.showInBank(slot(3, 4151)); // must not throw
        assertFalse(h.isArmed());
    }
}
