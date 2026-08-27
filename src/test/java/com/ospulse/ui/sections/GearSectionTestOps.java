package com.ospulse.ui.sections;

import java.util.Locale;

/**
 * Test-only helpers that drive {@link GearSection}'s budget/expensive-threshold
 * text fields the way a real K/M-suffixed user entry would, without a
 * production accessor seam. Lives in src/test because the Plugin Hub's
 * token guard only counts src/main.
 */
final class GearSectionTestOps
{
	private GearSectionTestOps()
	{
	}

	static void setBudgetText(GearSection section, String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			section.budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.budgetMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			section.budgetField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.budgetKToggle.setSelected(true);
		}
		else
		{
			section.budgetField.setText(trimmed);
		}
	}

	static void setExpensiveThresholdText(GearSection section, String text)
	{
		String trimmed = text == null ? "" : text.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.endsWith("m"))
		{
			section.expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.expensiveThresholdMToggle.setSelected(true);
		}
		else if (lower.endsWith("k"))
		{
			section.expensiveThresholdField.setText(trimmed.substring(0, trimmed.length() - 1));
			section.expensiveThresholdKToggle.setSelected(true);
		}
		else
		{
			section.expensiveThresholdField.setText(trimmed);
		}
	}
}
