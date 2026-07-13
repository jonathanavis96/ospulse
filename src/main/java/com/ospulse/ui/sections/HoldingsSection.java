package com.ospulse.ui.sections;

import com.ospulse.OSPulseConfig;
import com.ospulse.integration.PriceTrendService;
import com.ospulse.model.ItemStack;
import com.ospulse.session.HoldingPnl;
import com.ospulse.session.SessionSnapshot;
import com.ospulse.ui.CollapsibleSection;
import com.ospulse.ui.GpFormat;
import com.ospulse.ui.PanelWidgets;
import com.ospulse.wealth.WealthSnapshot;

import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Top holdings: the most valuable items in the player's wealth. Collapsed
 * summary shows the total value of the top holdings, plus - when price
 * trends are enabled - a value-weighted aggregate trend badge.
 *
 * <p>Rows are shown {@code config.holdingsPageSize()} at a time ("Show N
 * more" / "Show less"), and optionally carry a per-item price trend badge
 * (▲/▼ X.X%) sourced from {@link PriceTrendService}. Trend data is entirely
 * opt-in and off by default: with it disabled this section behaves exactly
 * as the plain holdings list did before, just paginated.
 *
 * <p>Each row also carries a per-item unrealized-drift badge (live value vs
 * session cost basis, see {@link HoldingPnl}) with the cost basis in the row
 * tooltip — a per-holding "how has this drifted since I picked it up"
 * readout. There is deliberately no session-wide "Unrealized P/L" stat row
 * here (or anywhere else in the panel): under the locked session model,
 * price drift on held/worn/inventory gear is zeroed and never independently
 * moves net worth, and the session's own unrealised figure — GE positions,
 * open/collectable Grand Exchange offers marked to market — lives in the
 * Session section instead.
 */
public final class HoldingsSection extends CollapsibleSection
{
	public static final String KEY = "holdings";

	private static final int DEFAULT_PAGE_SIZE = 5;

	private final ItemManager itemManager;
	private final OSPulseConfig config;
	private final PriceTrendService priceTrendService;
	private final ConfigManager configManager;
	private final Client client;

	private final JPanel holdingsListPanel;
	private final JPanel pagerRow;
	private final JButton moreButton;
	private final JButton lessButton;

	private List<ItemStack> lastHoldings = List.of();
	private Map<Integer, HoldingPnl> lastPnls = Map.of();
	private long total;
	private int visibleCount;
	/** Colored (green/red) HTML {@code <font>} span for the value-weighted aggregate trend badge, or null. */
	private String aggregateTrendBadgeHtml;

	public HoldingsSection(CollapseStore store, ItemManager itemManager, OSPulseConfig config,
		PriceTrendService priceTrendService)
	{
		this(store, itemManager, config, priceTrendService, null, null);
	}

	public HoldingsSection(CollapseStore store, ItemManager itemManager, OSPulseConfig config,
		PriceTrendService priceTrendService, ConfigManager configManager, Client client)
	{
		super(KEY, "Top holdings", store);
		this.itemManager = itemManager;
		this.config = config;
		this.priceTrendService = priceTrendService;
		this.configManager = configManager;
		this.client = client;
		this.visibleCount = pageSize();

		holdingsListPanel = new JPanel();
		holdingsListPanel.setLayout(new BoxLayout(holdingsListPanel, BoxLayout.Y_AXIS));
		holdingsListPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		holdingsListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body().add(holdingsListPanel);

		moreButton = new JButton();
		moreButton.setFont(FontManager.getRunescapeSmallFont());
		moreButton.addActionListener(e ->
		{
			visibleCount += pageSize();
			render();
		});

		lessButton = new JButton("Show less");
		lessButton.setFont(FontManager.getRunescapeSmallFont());
		lessButton.addActionListener(e ->
		{
			visibleCount = pageSize();
			render();
		});

		pagerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		pagerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pagerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		pagerRow.add(moreButton);
		pagerRow.add(lessButton);
		pagerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, pagerRow.getPreferredSize().height));
		body().add(pagerRow);

		if (priceTrendService != null)
		{
			priceTrendService.setOnUpdate(() -> SwingUtilities.invokeLater(this::render));
		}
	}

	@Override
	public void apply(SessionSnapshot snapshot)
	{
		WealthSnapshot wealth = snapshot.getWealth();
		lastHoldings = wealth == null ? List.of() : wealth.getTopHoldings();
		Map<Integer, HoldingPnl> pnls = new HashMap<>();
		for (HoldingPnl pnl : snapshot.getHoldingPnls())
		{
			pnls.put(pnl.getItemId(), pnl);
		}
		lastPnls = pnls;
		// Never shrink below a full page across a snapshot update, but do
		// preserve however far the user has paged.
		if (visibleCount < pageSize())
		{
			visibleCount = pageSize();
		}
		render();
	}

	/**
	 * No-op, retained so {@link com.ospulse.ui.OSPulsePanel#persistState()} can
	 * keep calling it unconditionally on plugin shutdown: this section no
	 * longer persists any cross-login state (the "since last login" Unrealized
	 * P/L baseline it used to write was removed along with the session-wide
	 * Unrealized P/L stat row — see the class javadoc).
	 */
	public void shutdown()
	{
	}

	/**
	 * Full panel reset (feature 11): collapses the "Show more" pagination back
	 * to a single page and clears the cached holdings so the list reads empty
	 * until the next snapshot repopulates it. Holdings themselves are live
	 * wealth (re-supplied every {@code apply}), so this only forgets transient
	 * view state.
	 */
	@Override
	public void resetState()
	{
		lastHoldings = List.of();
		lastPnls = Map.of();
		total = 0;
		visibleCount = pageSize();
		aggregateTrendBadgeHtml = null;
		render();
	}

	/**
	 * Collapsed-header summary: "Top holdings" value formatted compactly
	 * (e.g. {@code 2.1B} / {@code 950M}), plus - when a value-weighted trend
	 * is available - a separate colored (green up / red down) percentage
	 * badge with its arrow. Rendered as HTML in a plain (non-wrapping,
	 * non-HTML by default) {@code JLabel}: without the {@code <html>} wrapper
	 * long concatenated plain text just gets silently clipped by the header's
	 * fixed-width {@code BorderLayout.EAST} slot, which is what caused the
	 * value and trend badge to run together and overflow off the panel edge.
	 */
	@Override
	protected String summaryText()
	{
		String base = HoldingsFormat.compact(total);
		if (aggregateTrendBadgeHtml == null)
		{
			return "<html>" + base + "</html>";
		}
		return "<html>" + base + "&nbsp;&nbsp;" + aggregateTrendBadgeHtml + "</html>";
	}

	private void render()
	{
		holdingsListPanel.removeAll();

		boolean trendsOn = config != null && config.priceTrendEnabled();
		total = 0L;
		double weightedTrendSum = 0.0;
		double weightedValueSum = 0.0;

		if (lastHoldings.isEmpty())
		{
			holdingsListPanel.add(PanelWidgets.emptyRowLabel("No holdings tracked yet."));
		}
		else
		{
			int shown = Math.min(visibleCount, lastHoldings.size());
			List<Integer> shownIds = new ArrayList<>();

			for (int i = 0; i < lastHoldings.size(); i++)
			{
				ItemStack stack = lastHoldings.get(i);
				total += stack.value();

				OptionalDouble trend = (trendsOn && priceTrendService != null)
					? priceTrendService.getTrendPercent(stack.getId())
					: OptionalDouble.empty();
				if (trend.isPresent())
				{
					weightedTrendSum += stack.value() * trend.getAsDouble();
					weightedValueSum += stack.value();
				}

				if (i < shown)
				{
					shownIds.add(stack.getId());

					HoldingPnl pnl = lastPnls.get(stack.getId());
					String label = stack.getName() + " x" + String.format("%,d", stack.getQuantity());
					String value = GpFormat.format(stack.value());
					String unrlBadge = pnl != null && pnl.unrealized() != 0
						? unrealizedBadgeHtml(pnl.unrealized())
						: null;
					String right = trendsOn || unrlBadge != null
						? "<html>" + value
							+ (unrlBadge == null ? "" : "&nbsp;&nbsp;" + unrlBadge)
							+ (trendsOn ? "&nbsp;&nbsp;" + trendBadgeHtml(trend) : "")
							+ "</html>"
						: value;

					JPanel row = PanelWidgets.iconRow(itemManager, stack.getId(), label, right,
						ColorScheme.LIGHT_GRAY_COLOR);
					if (pnl != null)
					{
						row.setToolTipText("Cost basis: " + GpFormat.format(pnl.getCostBasis())
							+ "  |  Unrealized: " + signedGp(pnl.unrealized()));
					}
					holdingsListPanel.add(row);
				}
			}

			if (trendsOn && priceTrendService != null)
			{
				priceTrendService.prefetch(shownIds);
			}
		}

		aggregateTrendBadgeHtml = weightedValueSum > 0
			? trendBadgeHtml(OptionalDouble.of(weightedTrendSum / weightedValueSum))
			: null;

		boolean canShowMore = lastHoldings.size() > visibleCount;
		boolean canShowLess = visibleCount > pageSize();
		moreButton.setText("Show " + pageSize() + " more");
		moreButton.setVisible(canShowMore);
		lessButton.setVisible(canShowLess);
		pagerRow.setVisible(canShowMore || canShowLess);

		holdingsListPanel.revalidate();
		holdingsListPanel.repaint();
		body().revalidate();
		body().repaint();
		refreshSummary();
	}

	private int pageSize()
	{
		return config == null ? DEFAULT_PAGE_SIZE : Math.max(1, config.holdingsPageSize());
	}

	private static String signedGp(long value)
	{
		return (value > 0 ? "+" : "") + GpFormat.format(value);
	}

	/**
	 * Colored per-holding unrealized-drift badge (e.g. "+120k" / "-45k"):
	 * how far this holding's live value has moved from its session cost
	 * basis. Same visual idiom as the trend badge.
	 */
	private static String unrealizedBadgeHtml(long unrealized)
	{
		Color color = unrealized >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
		return "<font color='" + toHex(color) + "'>" + signedGp(unrealized) + "</font>";
	}

	private static String trendText(double pct)
	{
		String arrow = pct >= 0 ? "▲" : "▼"; // ▲ / ▼
		return arrow + " " + String.format("%.1f", Math.abs(pct)) + "%";
	}

	private static String trendBadgeHtml(OptionalDouble trend)
	{
		if (!trend.isPresent())
		{
			return "<font color='" + toHex(ColorScheme.LIGHT_GRAY_COLOR) + "'>—</font>"; // —
		}

		double pct = trend.getAsDouble();
		Color color = pct >= 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
		return "<font color='" + toHex(color) + "'>" + trendText(pct) + "</font>";
	}

	private static String toHex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
