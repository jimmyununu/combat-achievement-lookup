package com.calookup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * A collapsible tier heading with a coloured ribbon, the player's count for the tier and a
 * thin progress line, followed by the tier's task rows.
 * <p>
 * Rows are attached once per sort order ({@link #setRows}) and a filter only flips their
 * visibility ({@link #applyFilter}), which keeps Swing's cached sizes valid and makes
 * switching filters cheap even with hundreds of rows.
 */
class TierSection extends JPanel
{
	private final TaskTier tier;
	private final LookupPanel owner;

	private final JPanel header = new JPanel(new BorderLayout(6, 0));
	private final Ribbon ribbon = new Ribbon();
	private final JLabel title = new JLabel();
	private final JLabel count = new JLabel("", SwingConstants.RIGHT);
	private final RateBar progress;
	private final JPanel rows = new JPanel();
	private final JLabel empty = new JLabel();

	private Theme theme;
	private boolean collapsed;
	private boolean anyVisible;

	TierSection(TaskTier tier, LookupPanel owner, Theme theme)
	{
		this.tier = tier;
		this.owner = owner;
		this.theme = theme;

		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		// Gap to the next section
		setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		header.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 6));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

		header.add(ribbon, BorderLayout.WEST);

		JPanel middle = new JPanel();
		middle.setOpaque(false);
		middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setText(tier.getDisplayName());
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		middle.add(title);
		middle.add(Box.createVerticalStrut(3));
		progress = new RateBar(3, theme.tierColor(tier), theme.getTrack());
		progress.setAlignmentX(Component.LEFT_ALIGNMENT);
		middle.add(progress);
		header.add(middle, BorderLayout.CENTER);

		count.setFont(FontManager.getRunescapeSmallFont());
		header.add(count, BorderLayout.EAST);

		MouseAdapter toggle = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				owner.toggleCollapsed(tier);
			}
		};
		header.addMouseListener(toggle);
		title.addMouseListener(toggle);
		count.addMouseListener(toggle);
		middle.addMouseListener(toggle);
		add(header);

		rows.setOpaque(false);
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(rows);

		empty.setFont(FontManager.getRunescapeSmallFont());
		empty.setBorder(BorderFactory.createEmptyBorder(2, 24, 6, 6));
		empty.setAlignmentX(Component.LEFT_ALIGNMENT);
		empty.setVisible(false);
		add(empty);

		applyTheme(theme);
	}

	TaskTier getTier()
	{
		return tier;
	}

	void applyTheme(Theme theme)
	{
		this.theme = theme;
		Color tierColor = theme.tierColor(tier);
		header.setBackground(theme.getBackground());
		header.setOpaque(true);
		title.setForeground(tierColor);
		count.setForeground(theme.getMuted());
		empty.setForeground(theme.getMuted());
		progress.setColors(tierColor, theme.getTrack());
		ribbon.setColor(tierColor);
		repaint();
	}

	/** Attaches every row of this tier in display order; a no-op when nothing changed. */
	void setRows(List<TaskRow> ordered)
	{
		Component[] current = rows.getComponents();
		if (current.length == ordered.size())
		{
			boolean same = true;
			for (int i = 0; i < current.length; i++)
			{
				if (current[i] != ordered.get(i))
				{
					same = false;
					break;
				}
			}
			if (same)
			{
				return;
			}
		}
		rows.removeAll();
		for (TaskRow row : ordered)
		{
			rows.add(row);
		}
	}

	/**
	 * Shows only the rows the filter accepts and refreshes the count line.
	 *
	 * @param show     which tasks stay visible
	 * @param done     completed tasks in this tier for this boss, before filtering
	 * @param total    all tasks in this tier for this boss
	 * @param filter   the active filter, used for the "nothing here" message
	 * @param loggedIn whether completion is known
	 */
	void applyFilter(Predicate<CombatTask> show, int done, int total, TaskArranger.Filter filter, boolean loggedIn)
	{
		anyVisible = false;
		for (Component c : rows.getComponents())
		{
			if (c instanceof TaskRow)
			{
				boolean visible = show.test(((TaskRow) c).getTask());
				if (c.isVisible() != visible)
				{
					c.setVisible(visible);
				}
				anyVisible |= visible;
			}
		}
		updateCounts(done, total, loggedIn);
		if (filter == TaskArranger.Filter.INCOMPLETE)
		{
			empty.setText("Every " + tier.getDisplayName().toLowerCase() + " task done. Nice.");
		}
		else
		{
			empty.setText("No tasks.");
		}
		refreshVisibility();
	}

	/** Refreshes the count and progress line without touching the rows. */
	void updateCounts(int done, int total, boolean loggedIn)
	{
		if (loggedIn)
		{
			count.setText(done + " / " + total);
			progress.setFraction(total == 0 ? 0 : (double) done / total);
		}
		else
		{
			count.setText(total + (total == 1 ? " task" : " tasks"));
			progress.setFraction(0);
		}
	}

	void setCollapsed(boolean collapsed)
	{
		this.collapsed = collapsed;
		ribbon.setCollapsed(collapsed);
		refreshVisibility();
	}

	private void refreshVisibility()
	{
		rows.setVisible(!collapsed);
		empty.setVisible(!collapsed && !anyVisible);
	}

	/** The coloured bar with a small triangle showing whether the section is open. */
	private static class Ribbon extends JComponent
	{
		private Color color = Color.GRAY;
		private boolean collapsed;

		Ribbon()
		{
			Dimension d = new Dimension(12, 22);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			setOpaque(false);
		}

		void setColor(Color color)
		{
			this.color = color;
			repaint();
		}

		void setCollapsed(boolean collapsed)
		{
			this.collapsed = collapsed;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			int h = getHeight();
			g2.fillRoundRect(0, 0, 4, h, 4, 4);
			int cx = 9;
			int cy = h / 2;
			int[] xs;
			int[] ys;
			if (collapsed)
			{
				xs = new int[]{cx - 2, cx + 2, cx - 2};
				ys = new int[]{cy - 4, cy, cy + 4};
			}
			else
			{
				xs = new int[]{cx - 4, cx + 4, cx};
				ys = new int[]{cy - 2, cy - 2, cy + 3};
			}
			g2.fillPolygon(xs, ys, 3);
			g2.dispose();
		}
	}
}
