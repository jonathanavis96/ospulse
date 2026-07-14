package com.ospulse.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Layout contract for the session panel's include/exclude toggle rows.
 *
 * <p>The tick box used to LEAD its name ({@code [x] GE positions}), which
 * indented that name by the box's width — so a toggle row's name never lined
 * up with the plain stat-row names above it (Profit/GE flip), and the LAF's
 * default checkbox margin made the toggle rows sit further apart than
 * everything else. The box now FOLLOWS the name, right-aligned inside a
 * shared fixed-width column, so names align left and boxes align with each
 * other.
 */
public class PanelWidgetsTest
{
	/** Lays a hierarchy out without needing native peers. */
	private static void layout(Container container)
	{
		container.doLayout();
		for (Component child : container.getComponents())
		{
			if (child instanceof Container)
			{
				layout((Container) child);
			}
		}
	}

	/** x of {@code component} measured relative to {@code ancestor}. */
	private static int xWithin(Component component, Container ancestor)
	{
		int x = 0;
		for (Component c = component; c != null && c != ancestor; c = c.getParent())
		{
			x += c.getX();
		}
		return x;
	}

	private static JLabel nameLabelOf(JCheckBox checkbox)
	{
		for (Component c : ((Container) checkbox.getParent()).getComponents())
		{
			if (c instanceof JLabel)
			{
				return (JLabel) c;
			}
		}
		throw new AssertionError("toggle row has no name label");
	}

	/** A container holding one plain stat row then two toggle rows, laid out at panel width. */
	private static JPanel breakdown()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		return container;
	}

	@Test
	public void toggleRowNamesAlignWithPlainStatRowNames()
	{
		JPanel container = breakdown();
		PanelWidgets.statRow(container, "Profit");
		int column = PanelWidgets.toggleNameColumnWidth("GE positions", "Bank");
		JCheckBox ge = PanelWidgets.toggleStatRow(container, "GE positions", column).checkbox;
		JCheckBox bank = PanelWidgets.toggleStatRow(container, "Bank", column).checkbox;

		container.setSize(225, 200);
		layout(container);

		// The plain row's own name label is the alignment reference.
		JPanel plainRow = (JPanel) container.getComponent(0);
		JLabel plainName = null;
		for (Component c : plainRow.getComponents())
		{
			if (c instanceof JLabel && "Profit".equals(((JLabel) c).getText()))
			{
				plainName = (JLabel) c;
			}
		}
		assertTrue("plain stat row should have a 'Profit' name label", plainName != null);

		int reference = xWithin(plainName, container);
		assertEquals("GE positions name must start at the same x as a plain stat row name",
			reference, xWithin(nameLabelOf(ge), container));
		assertEquals("Bank name must start at the same x as a plain stat row name",
			reference, xWithin(nameLabelOf(bank), container));
	}

	@Test
	public void tickBoxesShareOneColumnEvenThoughTheNamesDiffer()
	{
		JPanel container = breakdown();
		int column = PanelWidgets.toggleNameColumnWidth("GE positions", "Bank");
		JCheckBox ge = PanelWidgets.toggleStatRow(container, "GE positions", column).checkbox;
		JCheckBox bank = PanelWidgets.toggleStatRow(container, "Bank", column).checkbox;

		container.setSize(225, 200);
		layout(container);

		assertEquals("tick boxes must line up despite 'Bank' being far shorter than 'GE positions'",
			xWithin(ge, container), xWithin(bank, container));
		assertTrue("tick box must sit to the RIGHT of its name, not lead it",
			xWithin(ge, container) > xWithin(nameLabelOf(ge), container));
	}

	/** The measured column must actually fit the widest name plus its box. */
	@Test
	public void nameColumnWidthFitsTheWidestNameAndItsBox()
	{
		int narrow = PanelWidgets.toggleNameColumnWidth("Bank");
		int wide = PanelWidgets.toggleNameColumnWidth("GE positions", "Bank");
		assertTrue("a wider name must widen the shared column", wide > narrow);

		JPanel container = breakdown();
		JCheckBox ge = PanelWidgets.toggleStatRow(container, "GE positions", wide).checkbox;
		container.setSize(225, 200);
		layout(container);

		JLabel name = nameLabelOf(ge);
		assertTrue("the name must not be squeezed below its natural width",
			name.getWidth() >= name.getPreferredSize().width);
	}

	/**
	 * "Equal spacing along the session": a toggle row must be EXACTLY as tall
	 * as the plain stat rows around it.
	 *
	 * <p>Asserted under BOTH look-and-feels on purpose. The first cut of this
	 * row relied on zeroing the stock JCheckBox's margin, and was verified by
	 * a test running under Metal (21x21 -> 13x13, a convincing win). The
	 * client runs FlatLaf, where the same call achieves almost nothing
	 * (19x19 -> 17x17), so the row shipped ~5px taller than its neighbours
	 * with a green test. Drawing our own icon makes the size LAF-independent
	 * — and this test only proves that if it actually exercises both.
	 */
	@Test
	public void toggleRowIsExactlyAStatRowTallUnderEveryLookAndFeel() throws Exception
	{
		LookAndFeel original = UIManager.getLookAndFeel();
		try
		{
			for (LookAndFeel laf : new LookAndFeel[]{new MetalLookAndFeel(), new FlatDarkLaf()})
			{
				UIManager.setLookAndFeel(laf);
				JPanel container = breakdown();
				PanelWidgets.statRow(container, "Profit");
				int column = PanelWidgets.toggleNameColumnWidth("Bank");
				PanelWidgets.toggleStatRow(container, "Bank", column);

				int plainHeight = container.getComponent(0).getPreferredSize().height;
				int toggleHeight = container.getComponent(1).getPreferredSize().height;
				assertEquals("under " + laf.getName() + ", toggle row must match a plain stat row exactly",
					plainHeight, toggleHeight);
			}
		}
		finally
		{
			UIManager.setLookAndFeel(original);
		}
	}

	/** The box size must come from us, not from whatever LAF happens to be installed. */
	@Test
	public void tickBoxSizeIsIdenticalUnderEveryLookAndFeel() throws Exception
	{
		LookAndFeel original = UIManager.getLookAndFeel();
		try
		{
			Dimension metal;
			Dimension flat;
			UIManager.setLookAndFeel(new MetalLookAndFeel());
			metal = PanelWidgets.toggleStatRow(breakdown(), "Bank", 80).checkbox.getPreferredSize();
			UIManager.setLookAndFeel(new FlatDarkLaf());
			flat = PanelWidgets.toggleStatRow(breakdown(), "Bank", 80).checkbox.getPreferredSize();

			assertEquals("box must be the same size in every LAF", metal, flat);
			assertEquals("box must be pinned to the stat-row text height",
				SmallCheckBoxIcon.DEFAULT_SIZE, metal.height);
		}
		finally
		{
			UIManager.setLookAndFeel(original);
		}
	}

	/**
	 * "Show breakdown" is a display-only toggle added outside breakdownPanel,
	 * but its box must still line up with the include/exclude boxes above it —
	 * and being the widest name, it is what sets the shared column.
	 */
	@Test
	public void displayOnlyToggleSharesTheColumnWithTheStatToggles()
	{
		JPanel container = breakdown();
		int column = PanelWidgets.toggleNameColumnWidth("GE positions", "Bank", "Show breakdown");
		JCheckBox ge = PanelWidgets.toggleStatRow(container, "GE positions", column).checkbox;
		JCheckBox show = PanelWidgets.toggleOnlyRow(container, "Show breakdown", column);

		container.setSize(225, 200);
		layout(container);

		assertEquals("'Show breakdown' box must share the column with 'GE positions'",
			xWithin(ge, container), xWithin(show, container));
		assertEquals("names must still start flush left",
			xWithin(nameLabelOf(ge), container), xWithin(nameLabelOf(show), container));
		assertTrue("the widest name must set the column, pushing the boxes right",
			column > PanelWidgets.toggleNameColumnWidth("GE positions", "Bank"));
	}

	/** The box lost its text, so the name has to stay a click target. */
	@Test
	public void clickingTheNameStillTogglesTheBox()
	{
		JPanel container = breakdown();
		int column = PanelWidgets.toggleNameColumnWidth("Bank");
		JCheckBox bank = PanelWidgets.toggleStatRow(container, "Bank", column).checkbox;
		assertTrue("toggles default to ON", bank.isSelected());

		nameLabelOf(bank).getMouseListeners()[0].mousePressed(null);
		assertEquals("clicking the name must toggle the box off", false, bank.isSelected());
	}
}
