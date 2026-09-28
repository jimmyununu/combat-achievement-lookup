package com.calookup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * One entry of the boss drop-down: a boss group with the points it offers and the points the
 * player has not earned yet. The list is ordered by points still available, so the bosses
 * with the most left to gain come first.
 */
final class BossOption
{
	/** Sentinel boss name for the entry that shows every task. */
	static final String ALL = "(All)";

	private final String boss;
	private final int totalPoints;
	private final int remainingPoints;
	private final boolean loggedIn;

	BossOption(String boss, int totalPoints, int remainingPoints, boolean loggedIn)
	{
		this.boss = boss;
		this.totalPoints = totalPoints;
		this.remainingPoints = remainingPoints;
		this.loggedIn = loggedIn;
	}

	String getBoss()
	{
		return boss;
	}

	int getTotalPoints()
	{
		return totalPoints;
	}

	int getRemainingPoints()
	{
		return remainingPoints;
	}

	/** Points still to gain when logged in, otherwise the boss's total. */
	int getAvailablePoints()
	{
		return loggedIn ? remainingPoints : totalPoints;
	}

	/** The text shown in the drop-down, e.g. {@code Zulrah  ·  22 pts left}. */
	String getLabel()
	{
		int pts = getAvailablePoints();
		return boss + "  ·  " + pts + (loggedIn ? (pts == 1 ? " pt left" : " pts left") : (pts == 1 ? " pt" : " pts"));
	}

	static boolean isAll(String boss)
	{
		return ALL.equals(boss);
	}

	/**
	 * Builds the drop-down entries: "(All)" first, then every boss ordered by points still
	 * available (descending) and name.
	 *
	 * @param bosses    boss display names
	 * @param tasksOf   tasks for each boss, in the same order as {@code bosses}
	 * @param allTasks  every task, for the "(All)" entry
	 * @param completed completed task IDs (empty when logged out)
	 * @param loggedIn  whether completion is known
	 */
	static List<BossOption> build(List<String> bosses, List<List<CombatTask>> tasksOf, List<CombatTask> allTasks,
		Set<Integer> completed, boolean loggedIn)
	{
		List<BossOption> options = new ArrayList<>();
		for (int i = 0; i < bosses.size(); i++)
		{
			List<CombatTask> tasks = tasksOf.get(i);
			int total = TaskArranger.pointsTotal(tasks);
			int remaining = total - TaskArranger.pointsDone(tasks, completed);
			options.add(new BossOption(bosses.get(i), total, remaining, loggedIn));
		}
		options.sort(Comparator.comparingInt(BossOption::getAvailablePoints).reversed()
			.thenComparing(BossOption::getBoss, String.CASE_INSENSITIVE_ORDER));

		int allTotal = TaskArranger.pointsTotal(allTasks);
		int allRemaining = allTotal - TaskArranger.pointsDone(allTasks, completed);
		options.add(0, new BossOption(ALL, allTotal, allRemaining, loggedIn));
		return options;
	}
}
