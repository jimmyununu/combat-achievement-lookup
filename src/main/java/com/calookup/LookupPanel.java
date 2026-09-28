package com.calookup;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel. Shows one boss at a time: a header with the player's progress, filter
 * and sort controls, then a collapsible section per tier. All methods run on the Swing thread.
 */
class LookupPanel extends PluginPanel
{
	private static final int INNER_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PluginPanel.BORDER_OFFSET;

	private final CombatAchievementLookupPlugin plugin;
	private Theme theme;
	private TaskArranger.Filter filter;
	private TaskArranger.SortMode sort;

	private final JPanel content = new JPanel();

	/** Thumbnail width requested from the wiki; scaled down to the header height when painted. */
	static final int PORTRAIT_SIZE = 160;

	// Header
	private final PortraitHeader header = new PortraitHeader();
	private final JLabel crest = new JLabel("Combat Achievements", SwingConstants.LEFT);
	private final JLabel bossTitle = new JLabel();
	private final JLabel bossSummary = new JLabel();
	private final JLabel pointsLine = new JLabel();
	private final RateBar bossProgress;
	private final JPanel alternatives = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final SearchableComboBox bossBox = new SearchableComboBox();

	// Controls
	private final JPanel controls = new JPanel();
	private final Map<TaskArranger.Filter, JToggleButton> filterButtons = new EnumMap<>(TaskArranger.Filter.class);
	private final JComboBox<TaskArranger.SortMode> sortBox = new JComboBox<>(TaskArranger.SortMode.values());

	// Body
	private final JPanel sections = new JPanel();
	private final JLabel notice = new JLabel();
	private final JLabel hint = new JLabel();

	// Footer
	private final JLabel source = new JLabel();

	private LookupView view;
	private CompletionRates rates = CompletionRates.EMPTY;
	private String ratesNotice;
	private final Map<String, String> labelToBoss = new HashMap<>();
	private final Map<String, String> bossToLabel = new HashMap<>();
	/** Rows currently shown, by task id. */
	private final Map<Integer, TaskRow> rowsById = new HashMap<>();
	/** Every row built for the current boss, so sorting and filtering reuse them instead of rebuilding. */
	private final Map<Integer, TaskRow> rowCache = new HashMap<>();
	/** Sections for the current boss; kept between filter and sort changes. */
	private final Map<TaskTier, TierSection> sectionsByTier = new EnumMap<>(TaskTier.class);
	/** Every task of the current boss per tier, in the current sort order. */
	private final Map<TaskTier, List<CombatTask>> tierTasks = new EnumMap<>(TaskTier.class);
	private final Set<TaskTier> collapsed = EnumSet.noneOf(TaskTier.class);
	private final Set<Integer> expanded = new HashSet<>();
	private final Map<Integer, WikiSummary> summaries = new HashMap<>();
	private final Map<Integer, String> summaryErrors = new HashMap<>();
	private final Set<Integer> summariesLoading = new HashSet<>();

	private boolean autoCollapseFinished;

	LookupPanel(CombatAchievementLookupPlugin plugin, Theme theme, TaskArranger.Filter filter,
		TaskArranger.SortMode sort, boolean autoCollapseFinished)
	{
		super();
		this.plugin = plugin;
		this.theme = theme;
		this.filter = filter;
		this.sort = sort;
		this.autoCollapseFinished = autoCollapseFinished;

		setLayout(new BorderLayout());
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		// Header: crest, title, summary and points line over an optional boss portrait
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		crest.setFont(FontManager.getRunescapeSmallFont());
		crest.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(crest);
		header.add(Box.createVerticalStrut(2));

		bossTitle.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		bossTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		bossTitle.setText("CA Lookup");
		header.add(bossTitle);
		header.add(Box.createVerticalStrut(4));

		bossSummary.setFont(FontManager.getRunescapeSmallFont());
		bossSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(bossSummary);

		pointsLine.setFont(FontManager.getRunescapeSmallFont());
		pointsLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		pointsLine.setVisible(false);
		header.add(pointsLine);
		content.add(header);
		content.add(Box.createVerticalStrut(4));

		bossProgress = new RateBar(6, theme.getAccent(), theme.getTrack());
		bossProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
		bossProgress.setVisible(false);
		content.add(bossProgress);
		content.add(Box.createVerticalStrut(6));

		alternatives.setOpaque(false);
		alternatives.setAlignmentX(Component.LEFT_ALIGNMENT);
		alternatives.setVisible(false);
		content.add(alternatives);

		bossBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		bossBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		bossBox.setPreferredSize(new Dimension(INNER_WIDTH, 26));
		bossBox.setToolTipText("Type to search, then pick a boss or monster group");
		bossBox.setOnSelect(label ->
		{
			String boss = labelToBoss.get(label);
			if (boss != null && (view == null || !boss.equals(view.getBoss())))
			{
				plugin.lookupBoss(boss);
			}
		});
		content.add(bossBox);
		content.add(Box.createVerticalStrut(8));

		// Controls: filter toggles + sort
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel filters = new JPanel(new GridLayout(1, 2, 3, 0));
		filters.setOpaque(false);
		filters.setAlignmentX(Component.LEFT_ALIGNMENT);
		filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		ButtonGroup group = new ButtonGroup();
		for (TaskArranger.Filter f : TaskArranger.Filter.values())
		{
			JToggleButton button = new JToggleButton(f.getLabel());
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setFocusable(false);
			button.setMargin(new Insets(2, 4, 2, 4));
			button.setContentAreaFilled(false);
			button.setOpaque(true);
			button.setSelected(f == filter);
			button.setToolTipText(f == TaskArranger.Filter.ALL
				? "Show every task, done or not"
				: "Hide the tasks you have already completed");
			button.addActionListener(e ->
			{
				this.filter = f;
				paintFilterButtons();
				applyFilter();
			});
			group.add(button);
			filters.add(button);
			filterButtons.put(f, button);
		}
		controls.add(filters);
		controls.add(Box.createVerticalStrut(4));

		sortBox.setFocusable(false);
		sortBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		sortBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		sortBox.setPreferredSize(new Dimension(INNER_WIDTH, 24));
		sortBox.setSelectedItem(sort);
		sortBox.setToolTipText("Order of tasks inside each tier");
		sortBox.addActionListener(e ->
		{
			Object selected = sortBox.getSelectedItem();
			if (selected instanceof TaskArranger.SortMode && selected != this.sort)
			{
				this.sort = (TaskArranger.SortMode) selected;
				rebuild();
			}
		});
		controls.add(sortBox);
		controls.setVisible(false);
		content.add(controls);
		content.add(Box.createVerticalStrut(6));

		// Notice (logged out, errors)
		notice.setFont(FontManager.getRunescapeSmallFont());
		notice.setAlignmentX(Component.LEFT_ALIGNMENT);
		notice.setVisible(false);
		content.add(notice);

		// Sections
		sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
		sections.setOpaque(false);
		sections.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(sections);

		// Hint shown before the first lookup
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		hint.setText(html("Right-click a boss or monster and choose <b>CA Lookup</b>, or type a name in the box above. "
			+ "Bosses are ordered by the points you can still earn; <b>(All)</b> lists every task."
			+ "<br><br>Tasks are grouped by tier. The percentage is how many players have completed each task "
			+ "according to the OSRS Wiki. Click a task for its wiki guide."));
		content.add(hint);

		content.add(Box.createVerticalStrut(8));

		// Footer
		source.setFont(FontManager.getRunescapeSmallFont());
		source.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(source);

		add(content, BorderLayout.NORTH);
		applyTheme(theme);
		updateSourceLabel();
	}

	/**
	 * Wrapping label. Swing's CSS engine scales {@code px} by 1.3 but maps {@code pt} one to one,
	 * so widths are given in points to get real pixels.
	 */
	private static String html(String body)
	{
		return "<html><body style='width: " + (INNER_WIDTH - 6) + "pt'>" + body + "</body></html>";
	}

	// ---- Called by the plugin ----

	/** Replaces the drop-down entries. Safe to call whenever points change; keeps the shown boss. */
	void setBossOptions(List<BossOption> options)
	{
		labelToBoss.clear();
		bossToLabel.clear();
		List<String> labels = new ArrayList<>();
		for (BossOption option : options)
		{
			labels.add(option.getLabel());
			labelToBoss.put(option.getLabel(), option.getBoss());
			bossToLabel.put(option.getBoss(), option.getLabel());
		}
		bossBox.setItems(labels);
		if (view != null)
		{
			bossBox.setCurrent(bossToLabel.get(view.getBoss()));
		}
	}

	void setAutoCollapseFinished(boolean autoCollapseFinished)
	{
		this.autoCollapseFinished = autoCollapseFinished;
	}

	void setDefaults(TaskArranger.Filter filter, TaskArranger.SortMode sort)
	{
		this.filter = filter;
		this.sort = sort;
		for (Map.Entry<TaskArranger.Filter, JToggleButton> e : filterButtons.entrySet())
		{
			e.getValue().setSelected(e.getKey() == filter);
		}
		sortBox.setSelectedItem(sort);
		paintFilterButtons();
		rebuild();
	}

	/** Shows a new boss. Expanded rows and collapsed tiers are reset. */
	void showLookup(LookupView view)
	{
		boolean sameBoss = this.view != null && this.view.getBoss().equals(view.getBoss());
		this.view = view;
		if (!sameBoss)
		{
			expanded.clear();
			collapsed.clear();
			rowCache.clear();
			sectionsByTier.clear();
			header.setPortrait(null);
			if (autoCollapseFinished && view.isLoggedIn())
			{
				Map<TaskTier, List<CombatTask>> all = TaskArranger.arrange(view.getTasks(), view.getCompleted(), rates,
					TaskArranger.Filter.ALL, TaskArranger.SortMode.NAME);
				for (Map.Entry<TaskTier, List<CombatTask>> e : all.entrySet())
				{
					if (!e.getValue().isEmpty()
						&& TaskArranger.countDone(e.getValue(), view.getCompleted()) == e.getValue().size())
					{
						collapsed.add(e.getKey());
					}
				}
			}
		}

		bossBox.setCurrent(bossToLabel.getOrDefault(view.getBoss(), view.getBoss()));

		hint.setVisible(false);
		controls.setVisible(true);
		bossProgress.setVisible(true);
		rebuild();
	}

	/**
	 * Re-checks every row after the player's completion bits changed. Rows stay attached; only
	 * their marks, the counts and their visibility under the current filter change.
	 */
	void updateCompletion(Set<Integer> completed, boolean loggedIn, Map<Integer, TaskProgress> progress, String points)
	{
		if (view == null)
		{
			return;
		}
		view = view.withCompletion(completed, loggedIn, progress, points);
		for (TaskRow row : rowsById.values())
		{
			int id = row.getTask().getId();
			row.setCompletion(completed.contains(id) && loggedIn, !loggedIn);
			row.setProgress(progress.get(id));
		}
		applyFilter();
	}

	/** Shows the boss's wiki image, ignored if the panel has moved on to another boss. */
	void setPortrait(String boss, BufferedImage image)
	{
		if (view != null && view.getBoss().equals(boss))
		{
			header.setPortrait(image);
		}
	}

	void updateRates(CompletionRates rates)
	{
		this.rates = rates;
		this.ratesNotice = null;
		updateSourceLabel();
		if (view != null)
		{
			rebuild();
		}
	}

	void setRatesNotice(String notice)
	{
		this.ratesNotice = notice;
		updateSourceLabel();
	}

	void showMessage(String message)
	{
		notice.setText(html(message));
		notice.setVisible(true);
		revalidate();
		repaint();
	}

	void showSummary(int taskId, WikiSummary summary)
	{
		summariesLoading.remove(taskId);
		summaryErrors.remove(taskId);
		summaries.put(taskId, summary);
		TaskRow row = rowsById.get(taskId);
		if (row != null)
		{
			row.showSummary(summary);
			revalidate();
			repaint();
		}
	}

	void showSummaryError(int taskId, String message)
	{
		summariesLoading.remove(taskId);
		summaryErrors.put(taskId, message);
		TaskRow row = rowsById.get(taskId);
		if (row != null)
		{
			row.showSummaryError(message);
			revalidate();
			repaint();
		}
	}

	void applyTheme(Theme theme)
	{
		this.theme = theme;
		setBackground(theme.getBackground());
		content.setBackground(theme.getBackground());
		content.setOpaque(true);
		crest.setForeground(theme.getAccent());
		bossTitle.setForeground(theme.getText());
		bossSummary.setForeground(theme.getMuted());
		pointsLine.setForeground(theme.getAccent());
		header.setBackgroundColor(theme.getBackground());
		bossProgress.setColors(theme.getAccent(), theme.getTrack());
		notice.setForeground(theme.getAccent());
		hint.setForeground(theme.getMuted());
		source.setForeground(theme.getMuted());
		bossBox.setBackground(theme.getCard());
		bossBox.setForeground(theme.getText());
		sortBox.setBackground(theme.getCard());
		sortBox.setForeground(theme.getText());
		paintFilterButtons();
		for (Component c : alternatives.getComponents())
		{
			if (c instanceof JButton)
			{
				styleChip((JButton) c, ((JButton) c).isEnabled());
			}
		}
		for (TierSection section : sectionsByTier.values())
		{
			section.applyTheme(theme);
		}
		for (TaskRow row : rowCache.values())
		{
			row.setTheme(theme);
		}
		revalidate();
		repaint();
	}

	// ---- Called by rows and sections ----

	void toggleExpanded(CombatTask task)
	{
		TaskRow row = rowsById.get(task.getId());
		if (row == null)
		{
			return;
		}
		boolean open = !row.isExpanded();
		row.setExpanded(open);
		if (open)
		{
			expanded.add(task.getId());
			fillSummary(row);
		}
		else
		{
			expanded.remove(task.getId());
		}
		revalidate();
		repaint();
	}

	void toggleCollapsed(TaskTier tier)
	{
		TierSection section = sectionsByTier.get(tier);
		if (section == null)
		{
			return;
		}
		boolean now = !collapsed.contains(tier);
		if (now)
		{
			collapsed.add(tier);
		}
		else
		{
			collapsed.remove(tier);
		}
		section.setCollapsed(now);
		revalidate();
		repaint();
	}

	void openWiki(CombatTask task)
	{
		plugin.openWiki(task);
	}

	// ---- Internals ----

	private void fillSummary(TaskRow row)
	{
		int id = row.getTask().getId();
		WikiSummary cached = summaries.get(id);
		if (cached != null)
		{
			row.showSummary(cached);
			return;
		}
		String error = summaryErrors.get(id);
		if (error != null && !summariesLoading.contains(id))
		{
			// Retry on the next open.
			summaryErrors.remove(id);
		}
		row.showSummaryLoading();
		if (summariesLoading.add(id))
		{
			plugin.requestSummary(row.getTask());
		}
	}

	/**
	 * Full pass: attaches every row of the current boss to its tier section in the current sort
	 * order, then applies the filter. Needed when the boss, the sort or the rates change.
	 */
	private void rebuild()
	{
		sections.removeAll();
		rowsById.clear();
		tierTasks.clear();
		notice.setVisible(false);

		if (view == null)
		{
			revalidate();
			repaint();
			return;
		}

		List<CombatTask> tasks = view.getTasks();
		Set<Integer> completed = view.getCompleted();
		boolean loggedIn = view.isLoggedIn();
		boolean allBosses = BossOption.isAll(view.getBoss());

		// Alternatives (other modes of the same raid, etc.)
		alternatives.removeAll();
		if (view.getAlternatives().size() > 1)
		{
			for (String alt : view.getAlternatives())
			{
				JButton chip = new JButton(shortName(alt, view.getAlternatives()));
				chip.setToolTipText(alt);
				chip.setFont(FontManager.getRunescapeSmallFont());
				chip.setFocusable(false);
				chip.setMargin(new Insets(1, 6, 1, 6));
				boolean current = alt.equals(view.getBoss());
				chip.setEnabled(!current);
				styleChip(chip, !current);
				chip.addActionListener(e -> plugin.lookupBoss(alt));
				alternatives.add(chip);
			}
			alternatives.setVisible(true);
		}
		else
		{
			alternatives.setVisible(false);
		}

		// Every task, in the current sort order; the filter is applied afterwards by visibility.
		Map<TaskTier, List<CombatTask>> ordered = TaskArranger.arrange(tasks, completed, rates,
			TaskArranger.Filter.ALL, sort);

		for (Map.Entry<TaskTier, List<CombatTask>> entry : ordered.entrySet())
		{
			TaskTier tier = entry.getKey();
			tierTasks.put(tier, entry.getValue());
			TierSection section = sectionsByTier.get(tier);
			if (section == null)
			{
				section = new TierSection(tier, this, theme);
				sectionsByTier.put(tier, section);
			}
			List<TaskRow> rows = new ArrayList<>(entry.getValue().size());
			for (CombatTask task : entry.getValue())
			{
				TaskRow row = rowCache.get(task.getId());
				if (row == null)
				{
					row = new TaskRow(task, this, theme, allBosses);
					rowCache.put(task.getId(), row);
				}
				row.setCompletion(completed.contains(task.getId()) && loggedIn, !loggedIn);
				row.setRate(rates.rateFor(task.getId()));
				row.setProgress(view.getProgress().get(task.getId()));
				boolean open = expanded.contains(task.getId());
				if (open != row.isExpanded())
				{
					row.setExpanded(open);
				}
				if (open)
				{
					fillSummary(row);
				}
				rowsById.put(task.getId(), row);
				rows.add(row);
			}
			section.setRows(rows);
			section.setCollapsed(collapsed.contains(tier));
			sections.add(section);
			sections.add(Box.createVerticalStrut(4));
		}

		if (ordered.isEmpty())
		{
			notice.setText(html("No Combat Achievements are filed under " + escape(view.getBoss()) + "."));
			notice.setVisible(true);
		}

		applyFilter();
	}

	/**
	 * Cheap pass: flips row visibility to match the filter and refreshes the header and counts.
	 * Attached rows keep their cached sizes, so this stays quick even with every tier open in
	 * the "(All)" view.
	 */
	private void applyFilter()
	{
		if (view == null)
		{
			return;
		}
		Set<Integer> completed = view.getCompleted();
		boolean loggedIn = view.isLoggedIn();
		updateHeader();
		Predicate<CombatTask> show = task -> TaskArranger.matches(task, completed, filter);
		for (Map.Entry<TaskTier, TierSection> e : sectionsByTier.entrySet())
		{
			List<CombatTask> tierAll = tierTasks.get(e.getKey());
			if (tierAll == null)
			{
				continue;
			}
			e.getValue().applyFilter(show, TaskArranger.countDone(tierAll, completed), tierAll.size(), filter, loggedIn);
		}
		revalidate();
		repaint();
	}

	/** Title, summary line and overall progress bar for the current view. */
	private void updateHeader()
	{
		List<CombatTask> tasks = view.getTasks();
		Set<Integer> completed = view.getCompleted();
		boolean loggedIn = view.isLoggedIn();
		boolean allBosses = BossOption.isAll(view.getBoss());

		bossTitle.setText(html("<b>" + (allBosses ? "All bosses" : escape(view.getBoss())) + "</b>"));
		int total = tasks.size();
		int done = TaskArranger.countDone(tasks, completed);
		int pointsTotal = TaskArranger.pointsTotal(tasks);
		int pointsDone = TaskArranger.pointsDone(tasks, completed);
		if (loggedIn)
		{
			bossSummary.setText(done + " of " + total + (total == 1 ? " task" : " tasks") + "  ·  "
				+ pointsDone + " of " + pointsTotal + " points");
			pointsLine.setText(view.getPointsLine() == null ? "" : view.getPointsLine());
			pointsLine.setVisible(view.getPointsLine() != null);
			bossProgress.setFraction(total == 0 ? 0 : (double) done / total);
			bossProgress.setToolTipText(done + " of " + total + " completed");
			notice.setVisible(false);
		}
		else
		{
			bossSummary.setText(total + (total == 1 ? " task" : " tasks") + "  ·  " + pointsTotal + " points");
			pointsLine.setVisible(false);
			bossProgress.setFraction(0);
			bossProgress.setToolTipText(null);
			notice.setText(html("Log in to see which of these you have completed."));
			notice.setVisible(true);
		}
	}

	/** Shortens "Theatre of Blood: Hard Mode" to "Hard Mode" when every alternative shares the prefix. */
	static String shortName(String name, List<String> all)
	{
		int colon = name.indexOf(':');
		if (colon < 0)
		{
			String prefix = null;
			for (String other : all)
			{
				int c = other.indexOf(':');
				if (c > 0 && other.substring(0, c).equals(name))
				{
					prefix = name;
					break;
				}
			}
			return prefix == null ? name : "Normal";
		}
		String prefix = name.substring(0, colon);
		for (String other : all)
		{
			if (!other.startsWith(prefix))
			{
				return name;
			}
		}
		return name.substring(colon + 1).trim();
	}

	private void styleChip(JButton chip, boolean enabled)
	{
		chip.setBackground(enabled ? theme.getCard() : theme.getAccent());
		chip.setForeground(enabled ? theme.getText() : theme.getBackground());
		chip.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		chip.setContentAreaFilled(false);
		chip.setOpaque(true);
	}

	/** The active filter is filled with the accent colour, bold, ticked and underlined; the other is a quiet card. */
	private void paintFilterButtons()
	{
		for (Map.Entry<TaskArranger.Filter, JToggleButton> e : filterButtons.entrySet())
		{
			JToggleButton b = e.getValue();
			boolean on = e.getKey() == filter;
			b.setText(on ? "✓ " + e.getKey().getLabel() : e.getKey().getLabel());
			b.setFont(on ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
			b.setBackground(on ? theme.getAccent() : theme.getCard());
			b.setForeground(on ? theme.getBackground() : theme.getMuted());
			b.setBorder(on
				? BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 2, 0, theme.getText()),
					BorderFactory.createEmptyBorder(3, 4, 1, 4))
				: BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 2, 0, theme.getCard()),
					BorderFactory.createEmptyBorder(3, 4, 1, 4)));
		}
	}

	private void updateSourceLabel()
	{
		String text;
		switch (rates.getSource())
		{
			case WIKI:
				String date = rates.getDataDateText();
				text = "Completion rates: OSRS Wiki" + (date == null ? "." : ", data from " + date + ".");
				break;
			case SNAPSHOT:
				text = "Completion rates: bundled wiki snapshot, data from " + rates.getDataDateText() + ".";
				break;
			case NONE:
			default:
				text = "Completion rates unavailable.";
				break;
		}
		if (ratesNotice != null)
		{
			text += " " + ratesNotice + ".";
		}
		text += " Task guides are wiki text under CC BY-NC-SA 3.0. Your own progress is read from the game.";
		source.setText(html(text));
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
