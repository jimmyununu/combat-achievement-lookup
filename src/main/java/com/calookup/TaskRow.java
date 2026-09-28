package com.calookup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
 * Rows are cheap to build on purpose: the "(All)" view creates several hundred of them. The
 * detail area is only built the first time a row is expanded, the type line is plain text, and
 * the title only uses a wrapping HTML label when it is too long to fit on one line.
 */
class TaskRow extends JPanel
{
	static final int INNER_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PluginPanel.BORDER_OFFSET - 4;
	private static final int TEXT_WIDTH = INNER_WIDTH - 24;
	/** Room for the title once the mark (14 + gap) and the rate column (44 + gap) are taken. */
	private static final int NAME_WIDTH = INNER_WIDTH - 12 - 20 - 50 - 8;
	/** Space left below each row instead of a separate spacer component. */
	private static final int GAP = 3;

	private final CombatTask task;
	private final LookupPanel owner;
	private final MouseAdapter clicks;

	private final JPanel head = new JPanel(new BorderLayout(6, 0));
	private final StatusMark mark;
	private final JLabel name = new JLabel();
	private final JLabel type = new JLabel();
	private final RateBar rateBar;
	private final JLabel rateLabel = new JLabel("", SwingConstants.RIGHT);
	private final JLabel progressLabel = new JLabel();

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
	private String typeText = "";
	private TaskProgress progress;
	private boolean done;
	private boolean unknown;
	private boolean expanded;
	private boolean hover;

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

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		// The bottom 3 px is the gap to the next row; the background is painted above it.
		setBorder(BorderFactory.createEmptyBorder(5, 6, 5 + GAP, 6));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setOpaque(false);

		mark = new StatusMark(theme.tierColor(task.getTier()), theme.getMuted());

		// Head: mark | name + type | rate
		head.setOpaque(false);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel markWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		markWrap.setOpaque(false);
		markWrap.add(mark);
		head.add(markWrap, BorderLayout.WEST);

		JPanel middle = new JPanel();
		middle.setOpaque(false);
		middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		type.setFont(FontManager.getRunescapeSmallFont());
		type.setAlignmentX(Component.LEFT_ALIGNMENT);
		middle.add(name);
		middle.add(type);
		head.add(middle, BorderLayout.CENTER);

		JPanel rateWrap = new JPanel();
		rateWrap.setOpaque(false);
		rateWrap.setLayout(new BoxLayout(rateWrap, BoxLayout.Y_AXIS));
		rateLabel.setFont(FontManager.getRunescapeSmallFont());
		rateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		rateBar = new RateBar(5, theme.tierColor(task.getTier()), theme.getTrack());
		rateBar.setPreferredSize(new Dimension(44, 5));
		rateBar.setMaximumSize(new Dimension(44, 5));
		rateBar.setAlignmentX(Component.RIGHT_ALIGNMENT);
		rateWrap.add(rateLabel);
		rateWrap.add(Box.createVerticalStrut(2));
		rateWrap.add(rateBar);
		head.add(rateWrap, BorderLayout.EAST);

		add(head);

		// Kill count / PB line, only shown when there is something to say
		progressLabel.setFont(FontManager.getRunescapeSmallFont());
		progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		progressLabel.setBorder(BorderFactory.createEmptyBorder(1, 20, 0, 0));
		progressLabel.setVisible(false);
		add(progressLabel);

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
		middle.addMouseListener(clicks);
		markWrap.addMouseListener(clicks);
		rateWrap.addMouseListener(clicks);
		name.addMouseListener(clicks);
		type.addMouseListener(clicks);
		progressLabel.addMouseListener(clicks);

		// Title: plain when it fits, wrapping HTML only when it would be cut off.
		Font nameFont = name.getFont();
		if (name.getFontMetrics(nameFont).stringWidth(task.getName()) <= NAME_WIDTH)
		{
			name.setText(task.getName());
		}
		else
		{
			name.setText(htmlNarrow(escape(task.getName())));
		}
		name.setToolTipText(task.getName());

		int points = task.getTier().getPoints();
		typeText = task.getType().getLabel() + "  ·  " + points + (points == 1 ? " pt" : " pts");
		if (showBoss)
		{
			typeText += "  ·  " + task.getBoss();
		}
		type.setText(typeText);
		type.setToolTipText(typeText);
		paintState();
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
		this.progress = progress;
		if (progress == null)
		{
			progressLabel.setVisible(false);
		}
		else
		{
			progressLabel.setText(progress.getText());
			progressLabel.setToolTipText(progress.isMet() ? "Target met" : "From your kill counts and personal bests");
			progressLabel.setVisible(true);
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

	/** Wrapping label for the title, which shares its line with the mark and the rate. */
	private static String htmlNarrow(String body)
	{
		return "<html><body style='width: " + NAME_WIDTH + "pt'>" + body + "</body></html>";
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
		mark.setColors(theme.tierColor(task.getTier()), theme.getMuted());
		rateBar.setColors(theme.tierColor(task.getTier()), theme.getTrack());
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
		mark.setState(done, unknown);
		paintState();
	}

	void setRate(Double rate)
	{
		if (rate == null)
		{
			rateLabel.setText("n/a");
			rateLabel.setToolTipText("The wiki has no completion rate for this task");
			rateBar.setFraction(-1);
		}
		else
		{
			rateLabel.setText(String.format(Locale.ROOT, rate >= 10 ? "%.0f%%" : "%.1f%%", rate));
			rateLabel.setToolTipText(String.format(Locale.ROOT, "%.1f%% of players have completed this task", rate));
			rateBar.setFraction(rate / 100.0);
		}
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

	private void paintState()
	{
		background = expanded ? theme.getCardOpen() : (hover ? theme.getCardHover() : theme.getCard());

		name.setForeground(done ? theme.getMuted() : theme.getText());
		type.setForeground(theme.getMuted());
		progressLabel.setForeground(progress != null && progress.isMet() ? theme.tierColor(TaskTier.EASY) : theme.getMuted());
		rateLabel.setForeground(done ? theme.getMuted() : theme.getText());
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
		setToolTipText(done ? "Completed" : (unknown ? "Log in to see whether you have completed this task" : "Not completed yet"));
		repaint();
	}
}
