package com.calookup;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * One task inside a tier section. Shows the done mark, the title, the type and the wiki
 * completion rate. Clicking it opens the in-game description, the wiki summary and a button
 * to open the full page.
 * <p>
 * The "(All)" view holds one row per task in the game, so the row head is a single painted
 * component whose text goes through a shared {@link JLabel} to match the panel's other labels.
 * The detail area is only built the first time a row is expanded.
 */
class TaskRow extends JPanel
{
	static final int INNER_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PluginPanel.BORDER_OFFSET - 4;
	private static final int TEXT_WIDTH = INNER_WIDTH - 24;
	/** Room for the title once the mark (14 + gap) and the rate column (44 + gap) are taken. */
	private static final int NAME_WIDTH = INNER_WIDTH - 12 - 20 - 50 - 8;
	/** Space left below each row instead of a separate spacer component. */
	private static final int GAP = 3;

	private static final int MARK_GAP = 6;
	private static final int RATE_WIDTH = 44;
	private static final int RATE_GAP = 6;
	private static final int BAR_HEIGHT = 5;
	private static final int BAR_GAP = 2;
	private static final int PROGRESS_GAP = 1;

	/** Shared labels that paint every row's text. */
	private static final JLabel TEXT_STAMP = stamp();
	private static final JLabel RATE_STAMP = stamp();

	private final CombatTask task;
	private final LookupPanel owner;
	private final MouseAdapter clicks;
	private final Head head = new Head();

	private final Font nameFont = FontManager.getRunescapeBoldFont();
	private final Font smallFont = FontManager.getRunescapeSmallFont();
	private final int nameHeight;
	private final int smallHeight;
	private final String[] nameLines;

	// Built lazily by ensureDetails()
	private JPanel details;
	private JLabel description;
	private JLabel wikiHeading;
	private JLabel wikiIntro;
	private JLabel wikiStrategy;
	private JLabel wikiStatus;
	private JButton openWiki;

	private Theme theme;
	private Color background;
	private boolean showBoss;
	private String typeText = "";
	private String rateText = "";
	private String rateTip;
	private double rateFraction = -1;
	private TaskProgress progress;
	private boolean done;
	private boolean unknown;
	private boolean expanded;
	private boolean hover;

	private static JLabel stamp()
	{
		JLabel label = new JLabel();
		label.setOpaque(false);
		label.setDoubleBuffered(false);
		return label;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (background != null)
		{
			g.setColor(background);
			g.fillRect(0, 0, getWidth(), getHeight() - GAP);
		}
	}

	/**
	 * @param showBoss whether to name the boss on the row, used when every boss is listed together
	 */
	TaskRow(CombatTask task, LookupPanel owner, Theme theme, boolean showBoss)
	{
		this.task = task;
		this.owner = owner;
		this.theme = theme;
		this.showBoss = showBoss;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		// The bottom 3 px is the gap to the next row; the background is painted above it.
		setBorder(BorderFactory.createEmptyBorder(5, 6, 5 + GAP, 6));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setOpaque(false);

		FontMetrics nameMetrics = getFontMetrics(nameFont);
		nameHeight = nameMetrics.getHeight();
		smallHeight = getFontMetrics(smallFont).getHeight();
		nameLines = wrap(task.getName(), nameMetrics, NAME_WIDTH);

		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(head);

		clicks = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				owner.toggleExpanded(task);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				paintState();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				paintState();
			}
		};
		addMouseListener(clicks);
		head.addMouseListener(clicks);

		updateTypeLine();
		paintState();
	}

	/** Breaks a title into lines that fit the width, at spaces. */
	static String[] wrap(String text, FontMetrics metrics, int width)
	{
		if (metrics.stringWidth(text) <= width)
		{
			return new String[]{text};
		}
		List<String> lines = new ArrayList<>(3);
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (line.length() == 0)
			{
				line.append(word);
			}
			else if (metrics.stringWidth(line + " " + word) <= width)
			{
				line.append(' ').append(word);
			}
			else
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines.toArray(new String[0]);
	}

	/** Names the boss on the type line, for the "(All)" view where rows of many bosses are mixed. */
	void setShowBoss(boolean showBoss)
	{
		if (this.showBoss == showBoss)
		{
			return;
		}
		this.showBoss = showBoss;
		updateTypeLine();
		head.repaint();
	}

	private void updateTypeLine()
	{
		int points = task.getTier().getPoints();
		typeText = task.getType().getLabel() + "  ·  " + points + (points == 1 ? " pt" : " pts");
		if (showBoss)
		{
			typeText += "  ·  " + task.getBoss();
		}
	}

	/**
	 * Shows the player's own standing (kill count or PB) on a line under the row head. The line
	 * turns green once the number already meets the task's target.
	 */
	void setProgress(TaskProgress progress)
	{
		boolean same = progress == null ? this.progress == null
			: this.progress != null && progress.getText().equals(this.progress.getText()) && progress.isMet() == this.progress.isMet();
		if (same)
		{
			return;
		}
		boolean heightChanged = (progress == null) != (this.progress == null);
		this.progress = progress;
		if (heightChanged)
		{
			head.revalidate();
		}
		paintState();
	}

	CombatTask getTask()
	{
		return task;
	}

	private static String html(String body)
	{
		return "<html><body style='width: " + TEXT_WIDTH + "pt'>" + body + "</body></html>";
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
	}

	/** Builds the expandable area the first time it is needed. */
	private void ensureDetails()
	{
		if (details != null)
		{
			return;
		}
		details = new JPanel();
		details.setOpaque(false);
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		description = new JLabel(html(escape(task.getDescription())));
		description.setFont(FontManager.getRunescapeSmallFont());
		description.setAlignmentX(Component.LEFT_ALIGNMENT);
		description.addMouseListener(clicks);
		details.add(description);
		details.add(Box.createVerticalStrut(6));

		wikiHeading = new JLabel("From the wiki");
		wikiHeading.setFont(FontManager.getRunescapeBoldFont());
		wikiHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(wikiHeading);

		wikiStatus = new JLabel();
		wikiStatus.setFont(FontManager.getRunescapeSmallFont());
		wikiStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(wikiStatus);

		wikiIntro = new JLabel();
		wikiIntro.setFont(FontManager.getRunescapeSmallFont());
		wikiIntro.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(wikiIntro);

		wikiStrategy = new JLabel();
		wikiStrategy.setFont(FontManager.getRunescapeSmallFont());
		wikiStrategy.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(wikiStrategy);
		details.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		openWiki = new JButton("Open on wiki");
		openWiki.setFont(FontManager.getRunescapeSmallFont());
		openWiki.setMargin(new Insets(2, 8, 2, 8));
		openWiki.setFocusable(false);
		openWiki.setToolTipText("Open the full task page in your browser");
		openWiki.addActionListener(e -> owner.openWiki(task));
		buttons.add(openWiki);
		details.add(buttons);
		details.add(Box.createVerticalStrut(4));

		details.setVisible(false);
		add(details);
	}

	void setTheme(Theme theme)
	{
		this.theme = theme;
		paintState();
	}

	void setCompletion(boolean done, boolean unknown)
	{
		if (this.done == done && this.unknown == unknown)
		{
			return;
		}
		this.done = done;
		this.unknown = unknown;
		paintState();
	}

	void setRate(Double rate)
	{
		if (rate == null)
		{
			rateText = "n/a";
			rateTip = "The wiki has no completion rate for this task";
			rateFraction = -1;
		}
		else
		{
			rateText = String.format(Locale.ROOT, rate >= 10 ? "%.0f%%" : "%.1f%%", rate);
			rateTip = String.format(Locale.ROOT, "%.1f%% of players have completed this task", rate);
			rateFraction = rate / 100.0;
		}
		head.repaint();
	}

	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		if (expanded)
		{
			ensureDetails();
		}
		if (details != null)
		{
			details.setVisible(expanded);
		}
		paintState();
	}

	boolean isExpanded()
	{
		return expanded;
	}

	void showSummaryLoading()
	{
		ensureDetails();
		wikiStatus.setText(html("Fetching from the wiki…"));
		wikiStatus.setVisible(true);
		wikiIntro.setVisible(false);
		wikiStrategy.setVisible(false);
	}

	void showSummary(WikiSummary summary)
	{
		ensureDetails();
		if (summary == null || summary.isEmpty())
		{
			wikiStatus.setText(html("The wiki page has no description yet."));
			wikiStatus.setVisible(true);
			wikiIntro.setVisible(false);
			wikiStrategy.setVisible(false);
			return;
		}
		wikiStatus.setVisible(false);
		wikiIntro.setText(html(escape(summary.getIntro())));
		wikiIntro.setVisible(!summary.getIntro().isEmpty());
		if (summary.getStrategy().isEmpty())
		{
			wikiStrategy.setVisible(false);
		}
		else
		{
			wikiStrategy.setText(html("<b>Strategy</b><br>" + escape(summary.getStrategy())));
			wikiStrategy.setVisible(true);
		}
	}

	void showSummaryError(String message)
	{
		ensureDetails();
		wikiStatus.setText(html(escape(message)));
		wikiStatus.setVisible(true);
		wikiIntro.setVisible(false);
		wikiStrategy.setVisible(false);
	}

	private String rowTip()
	{
		return done ? "Completed" : (unknown ? "Log in to see whether you have completed this task" : "Not completed yet");
	}

	private void paintState()
	{
		background = expanded ? theme.getCardOpen() : (hover ? theme.getCardHover() : theme.getCard());
		if (details != null)
		{
			description.setForeground(theme.getText());
			wikiHeading.setForeground(theme.tierColor(task.getTier()));
			wikiStatus.setForeground(theme.getMuted());
			wikiIntro.setForeground(theme.getText());
			wikiStrategy.setForeground(theme.getText());
			openWiki.setBackground(theme.getCardHover());
			openWiki.setForeground(theme.getText());
		}
		String tip = rowTip();
		setToolTipText(tip);
		head.setToolTipText(tip);
		repaint();
	}

	/** Title lines and type line beside the mark and rate column, plus the progress line. */
	private int headHeight()
	{
		int text = nameLines.length * nameHeight + smallHeight;
		int rate = smallHeight + BAR_GAP + BAR_HEIGHT;
		int h = Math.max(Math.max(text, rate), StatusMark.SIZE);
		if (progress != null)
		{
			h += PROGRESS_GAP + smallHeight;
		}
		return h;
	}

	/** Paints one line of text through a shared label. */
	private static void stamp(JLabel stamp, Graphics2D g, String text, Font font, Color color, int align,
		int x, int y, int w, int h)
	{
		if (w <= 0 || h <= 0)
		{
			return;
		}
		stamp.setFont(font);
		stamp.setForeground(color);
		stamp.setHorizontalAlignment(align);
		stamp.setText(text);
		if (stamp.getWidth() != w || stamp.getHeight() != h)
		{
			stamp.setSize(w, h);
		}
		Graphics2D cg = (Graphics2D) g.create(x, y, w, h);
		try
		{
			stamp.paint(cg);
		}
		finally
		{
			cg.dispose();
		}
	}

	/** The single painted component that is the row head. */
	private final class Head extends JComponent
	{
		Head()
		{
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(INNER_WIDTH - 12, headHeight());
		}

		@Override
		public Dimension getMinimumSize()
		{
			return new Dimension(StatusMark.SIZE + MARK_GAP + RATE_GAP + RATE_WIDTH, headHeight());
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Short.MAX_VALUE, headHeight());
		}

		/** Each part of the head has its own tooltip. */
		@Override
		public String getToolTipText(MouseEvent e)
		{
			int x = e.getX();
			int y = e.getY();
			int textX = StatusMark.SIZE + MARK_GAP;
			int nameBottom = nameLines.length * nameHeight;
			int typeBottom = nameBottom + smallHeight;
			if (progress != null && x >= textX && y >= typeBottom + PROGRESS_GAP && y < typeBottom + PROGRESS_GAP + smallHeight)
			{
				return progress.isMet() ? "Target met" : "From your kill counts and personal bests";
			}
			if (x >= getWidth() - RATE_WIDTH - RATE_GAP / 2 && y < typeBottom)
			{
				return rateTip;
			}
			if (x >= textX)
			{
				if (y < nameBottom)
				{
					return task.getName();
				}
				if (y < typeBottom)
				{
					return typeText;
				}
			}
			return rowTip();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g;
			int w = getWidth();
			int textX = StatusMark.SIZE + MARK_GAP;
			int textW = w - textX - RATE_GAP - RATE_WIDTH;
			int rateX = w - RATE_WIDTH;
			Color tierColor = theme.tierColor(task.getTier());
			Color muted = theme.getMuted();

			StatusMark.paint(g2, 0, 0, done, unknown, tierColor, muted);

			int y = 0;
			Color nameColor = done ? muted : theme.getText();
			for (String line : nameLines)
			{
				stamp(TEXT_STAMP, g2, line, nameFont, nameColor, SwingConstants.LEFT, textX, y, textW, nameHeight);
				y += nameHeight;
			}
			stamp(TEXT_STAMP, g2, typeText, smallFont, muted, SwingConstants.LEFT, textX, y, textW, smallHeight);
			y += smallHeight;
			if (progress != null)
			{
				y += PROGRESS_GAP;
				Color color = progress.isMet() ? theme.tierColor(TaskTier.EASY) : muted;
				stamp(TEXT_STAMP, g2, progress.getText(), smallFont, color, SwingConstants.LEFT, textX, y, w - textX, smallHeight);
			}

			stamp(RATE_STAMP, g2, rateText, smallFont, done ? muted : theme.getText(), SwingConstants.RIGHT,
				rateX, 0, RATE_WIDTH, smallHeight);
			RateBar.paint(g2, rateX, smallHeight + BAR_GAP, RATE_WIDTH, BAR_HEIGHT, rateFraction, tierColor, theme.getTrack());
		}
	}
}
