package com.ospulse.ui;

import com.ospulse.session.SessionSnapshot;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A titled panel section that the user can collapse by clicking its header.
 *
 * <p>When expanded it shows a {@linkplain #body() body} panel; when collapsed
 * the body is hidden and a one-line summary (supplied by {@link #summaryText()})
 * is shown on the right of the header instead. Collapsed state is persisted via
 * the injected {@link CollapseStore}, so it survives a client restart.
 *
 * <p>Subclasses build their body in their constructor (adding to
 * {@link #body()}), refresh their labels in {@link #apply(SessionSnapshot)}, and
 * must call {@link #refreshSummary()} at the end of {@code apply} so the
 * collapsed header stays current even while hidden. This mirrors the
 * collapsible loot-source idiom the panel already used, generalised to whole
 * sections (feature 4: minimize-all).
 */
public abstract class CollapsibleSection extends JPanel
{
	/** Persistence hook for which sections the user has collapsed. */
	public interface CollapseStore
	{
		boolean isCollapsed(String key);

		void setCollapsed(String key, boolean collapsed);
	}

	private final String key;
	private final CollapseStore store;
	private final JLabel triangleLabel;
	private final JLabel summaryLabel;
	private final JPanel body;
	private boolean collapsed;

	protected CollapsibleSection(String key, String title, CollapseStore store)
	{
		this.key = key;
		this.store = store;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(5, 6, 5, 6));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		triangleLabel = new JLabel();
		triangleLabel.setForeground(ColorScheme.BRAND_ORANGE);
		triangleLabel.setFont(FontManager.getRunescapeBoldFont());

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());

		JPanel left = new JPanel(new BorderLayout(4, 0));
		left.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		left.add(triangleLabel, BorderLayout.WEST);
		left.add(titleLabel, BorderLayout.CENTER);

		summaryLabel = new JLabel();
		summaryLabel.setForeground(Color.WHITE);
		summaryLabel.setFont(FontManager.getRunescapeSmallFont());
		summaryLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		header.add(left, BorderLayout.CENTER);
		header.add(summaryLabel, BorderLayout.EAST);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggle();
			}
		});
		add(header);

		body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(body);

		collapsed = store != null && store.isCollapsed(key);
		applyCollapsedState();
	}

	/** The container subclasses add their content to. */
	protected final JPanel body()
	{
		return body;
	}

	/**
	 * The one-line summary shown on the header when collapsed. Called both on
	 * toggle and after every {@link #apply(SessionSnapshot)} via
	 * {@link #refreshSummary()}. May return {@code "-"} before any data.
	 */
	protected abstract String summaryText();

	/** Refresh this section's labels from a new snapshot. */
	public abstract void apply(SessionSnapshot snapshot);

	/**
	 * Removes any game-canvas overlays this section's categories may have
	 * added (see {@code com.ospulse.ui.category.CategoryController}'s
	 * "Add to canvas" action). No-op by default; sections that wire up
	 * per-category canvas overlays override this so a plugin toggle or
	 * shutdown doesn't leak overlays. Called from the Swing EDT.
	 */
	public void removeAllCategoryOverlays()
	{
	}

	/** Subclasses MUST call this at the end of {@link #apply}. */
	protected final void refreshSummary()
	{
		if (collapsed)
		{
			summaryLabel.setText(summaryText());
		}
	}

	private void toggle()
	{
		collapsed = !collapsed;
		if (store != null)
		{
			store.setCollapsed(key, collapsed);
		}
		applyCollapsedState();
	}

	private void applyCollapsedState()
	{
		triangleLabel.setText(collapsed ? "▸" : "▾"); // ▸ / ▾
		body.setVisible(!collapsed);
		summaryLabel.setText(collapsed ? summaryText() : "");
		revalidate();
		repaint();
	}
}
