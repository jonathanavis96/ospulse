package com.ospulse.integration;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BankRecommendationHighlighterTest
{
    private BankTagsService tags;
    private TagManager tagManager;
    private ClientThread clientThread;
    private BankRecommendationHighlighter highlighter;

    @Before
    public void setUp()
    {
        tags = mock(BankTagsService.class);
        tagManager = mock(TagManager.class);
        clientThread = mock(ClientThread.class);
        // Run any client-thread runnable synchronously.
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
            .when(clientThread).invoke(any(Runnable.class));
        highlighter = new BankRecommendationHighlighter(tags, tagManager, clientThread);
    }

    @Test
    public void showInBankTagsIdsAndOpensTag()
    {
        Set<Integer> ids = new LinkedHashSet<>();
        ids.add(4151);
        ids.add(11802);
        highlighter.showInBank(ids);

        // reserved tag cleared first, then each id tagged
        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(4151, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tagManager).addTag(11802, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tags).openBankTag(eq(BankRecommendationHighlighter.RESERVED_TAG), anyInt());
        assertTrue(highlighter.isArmed());
    }

    @Test
    public void clearRemovesTagAndCloses()
    {
        highlighter.showInBank(java.util.Collections.singleton(4151));
        reset(tagManager, tags);
        highlighter.clear();
        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tags).closeBankTag();
        assertFalse(highlighter.isArmed());
    }

    @Test
    public void reShowReplacesRatherThanAccumulates()
    {
        highlighter.showInBank(java.util.Collections.singleton(4151));
        highlighter.showInBank(java.util.Collections.singleton(11802));
        // removeTag called on each show to reset before re-tagging
        verify(tagManager, times(2)).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(11802, BankRecommendationHighlighter.RESERVED_TAG, false);
    }

    @Test
    public void reapplyIfArmedReappliesReservedTagWhenArmed()
    {
        highlighter.showInBank(java.util.Collections.singleton(4151));
        reset(tagManager, tags);

        highlighter.reapplyIfArmed();

        // re-tags the still-current id set and re-opens the reserved tag
        verify(tagManager).removeTag(BankRecommendationHighlighter.RESERVED_TAG);
        verify(tagManager).addTag(4151, BankRecommendationHighlighter.RESERVED_TAG, false);
        verify(tags).openBankTag(eq(BankRecommendationHighlighter.RESERVED_TAG), anyInt());
    }

    @Test
    public void reapplyIfArmedNoOpWhenNotArmed()
    {
        // never armed
        highlighter.reapplyIfArmed();
        verifyNoInteractions(tagManager, tags);

        // and after a clear
        highlighter.showInBank(java.util.Collections.singleton(4151));
        highlighter.clear();
        reset(tagManager, tags);
        highlighter.reapplyIfArmed();
        verifyNoInteractions(tagManager, tags);
    }

    @Test
    public void nullServicesNoOp()
    {
        BankRecommendationHighlighter h = new BankRecommendationHighlighter(null, null, clientThread);
        h.showInBank(java.util.Collections.singleton(4151)); // must not throw
        assertFalse(h.isArmed());
    }
}
