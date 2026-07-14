package com.ospulse.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CentFormatTest
{
	@Test
	public void integerOnlyValueHasNoDecimalSpan()
	{
		assertEquals("<font color='#FFFFFF'>500</font>", CentFormat.fragment("500"));
	}

	@Test
	public void integerAndDecimalAreSplitOnFirstDot()
	{
		assertEquals("<font color='#FFFFFF'>1</font><font color='#8C8C8C'>.5</font>",
			CentFormat.fragment("1.5"));
	}

	@Test
	public void suffixAfterDecimalRendersBrightAfterTheDimmedDecimal()
	{
		assertEquals("<font color='#FFFFFF'>1</font><font color='#8C8C8C'>.5</font><font color='#FFFFFF'>m</font>",
			CentFormat.fragment("1.5m"));
	}

	@Test
	public void percentSuffixRendersBrightAfterTheDimmedDecimal()
	{
		assertEquals("<font color='#FFFFFF'>62</font><font color='#8C8C8C'>.3</font><font color='#FFFFFF'>%</font>",
			CentFormat.fragment("62.3%"));
	}

	@Test
	public void roundThousandsSuffixMergesIntoASingleBrightTag()
	{
		// GpFormat.format(100_000) -> "100k": no decimal point at all, so the
		// whole thing (including the k) stays bright as one merged <font> tag.
		assertEquals("<font color='#FFFFFF'>100k</font>",
			CentFormat.fragment("100k"));
	}

	@Test
	public void roundMillionsAndBillionsSuffixStaysBright()
	{
		assertEquals("<font color='#FFFFFF'>1m</font>",
			CentFormat.fragment("1m"));
		assertEquals("<font color='#FFFFFF'>2b</font>",
			CentFormat.fragment("2b"));
	}

	@Test
	public void negativeRoundSuffixKeepsSignWithTheIntegerAndStaysBright()
	{
		assertEquals("<font color='#FFFFFF'>-3m</font>",
			CentFormat.fragment("-3m"));
	}

	@Test
	public void commaGroupedIntegerHasNoDimmedPart()
	{
		assertEquals("<font color='#FFFFFF'>1,234</font>", CentFormat.fragment("1,234"));
	}

	@Test
	public void greenRoundSuffixMergesIntoASingleBrightGreenTag()
	{
		assertEquals("<font color='#37F046'>5m</font>",
			CentFormat.greenFragment("5m"));
	}

	@Test
	public void explicitColorPairingOverridesDefaults()
	{
		assertEquals("<font color='#111111'>1</font><font color='#222222'>.5</font>",
			CentFormat.fragment("1.5", "#111111", "#222222"));
	}

	@Test
	public void greenFragmentUsesGreenAndDullGreen()
	{
		assertEquals("<font color='#37F046'>1</font><font color='#1E8427'>.5</font>",
			CentFormat.greenFragment("1.5"));
	}

	@Test
	public void greenFragmentIntegerOnlyStillUsesGreen()
	{
		assertEquals("<font color='#37F046'>500</font>", CentFormat.greenFragment("500"));
	}

	@Test
	public void redFragmentUsesRedAndDullRed()
	{
		assertEquals("<font color='#E61E1E'>-1</font><font color='#7F1111'>.5</font>",
			CentFormat.redFragment("-1.5"));
	}

	@Test
	public void redFragmentIntegerOnlyStillUsesRed()
	{
		assertEquals("<font color='#E61E1E'>-500</font>", CentFormat.redFragment("-500"));
	}

	@Test
	public void htmlWrapsDefaultFragmentInHtmlTags()
	{
		assertEquals("<html><font color='#FFFFFF'>1</font><font color='#8C8C8C'>.5</font></html>",
			CentFormat.html("1.5"));
	}

	@Test
	public void htmlWithColorPairingWrapsInHtmlTags()
	{
		assertEquals("<html><font color='#37F046'>1</font><font color='#1E8427'>.5</font></html>",
			CentFormat.html("1.5", CentFormat.GREEN, CentFormat.GREEN_DIM));
	}

	@Test
	public void dullGreenIsClearlyDarkerThanGreen()
	{
		assertEquals("#37F046", CentFormat.GREEN);
		assertEquals("#1E8427", CentFormat.GREEN_DIM);
	}

	@Test
	public void dullRedIsClearlyDarkerThanRed()
	{
		assertEquals("#E61E1E", CentFormat.RED);
		assertEquals("#7F1111", CentFormat.RED_DIM);
	}

	// --- dim(): the single source of truth GREEN_DIM/RED_DIM are derived
	// from, and any caller with its own integer colour (e.g. a highlighted
	// row's accent colour) can use directly instead of hand-picking a shade. ---

	@Test
	public void dimScalesWhiteDownToTheStandingGreyDecimalColor()
	{
		assertEquals("#8C8C8C", CentFormat.dim("#FFFFFF"));
	}

	@Test
	public void dimOfGreenMatchesTheStandingGreenDim()
	{
		assertEquals(CentFormat.GREEN_DIM, CentFormat.dim(CentFormat.GREEN));
	}

	@Test
	public void dimOfRedMatchesTheStandingRedDim()
	{
		assertEquals(CentFormat.RED_DIM, CentFormat.dim(CentFormat.RED));
	}

	@Test
	public void dimOfBlackStaysBlack()
	{
		assertEquals("#000000", CentFormat.dim("#000000"));
	}

	// --- fragment(formatted, color): the 2-arg overload used for a context
	// colour outside the standing GREEN/RED pairings, e.g. the best-ranked
	// style/spell row rendered in the panel's brand accent colour. ---

	@Test
	public void twoArgFragmentDimsTheGivenColorForTheDecimal()
	{
		String orange = "#DC8A00";
		assertEquals("<font color='#DC8A00'>1</font><font color='" + CentFormat.dim(orange) + "'>.5</font>",
			CentFormat.fragment("1.5", orange));
	}

	@Test
	public void twoArgFragmentMatchesThreeArgFragmentWithDimmedColor()
	{
		String orange = "#DC8A00";
		assertEquals(CentFormat.fragment("1.5", orange, CentFormat.dim(orange)),
			CentFormat.fragment("1.5", orange));
	}

	@Test
	public void twoArgFragmentIntegerOnlyStillUsesTheGivenColor()
	{
		assertEquals("<font color='#DC8A00'>500</font>", CentFormat.fragment("500", "#DC8A00"));
	}
}
