package com.calookup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure filtering and sorting of a boss's tasks. Tier sections are always kept; the options
 * only decide which rows appear inside each section and in what order.
 * <p>
 * Public because {@link Filter} and {@link SortMode} are config value types, and RuneLite's
 * config proxy lives in another module that can only see public classes.
 */
public final class TaskArranger
{
	public enum Filter
	{
		ALL("All"),
		INCOMPLETE("Incomplete");

		private final String label;

		Filter(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum SortMode
	{
		WIKI_RATE("Most completed first"),
		WIKI_RATE_ASC("Rarest first"),
		NAME("Name A-Z");

		private final String label;

		SortMode(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private TaskArranger()
	{
	}

	/**
	 * Groups the tasks by tier, applying the filter and sort inside each tier. Tiers with no
	 * tasks for this boss are omitted; tiers whose tasks were all filtered out are kept with an
	 * empty list so the section can say "all done".
	 */
	static Map<TaskTier, List<CombatTask>> arrange(List<CombatTask> tasks, Set<Integer> completed,
		CompletionRates rates, Filter filter, SortMode sort)
	{
		Map<TaskTier, List<CombatTask>> out = new EnumMap<>(TaskTier.class);
		for (CombatTask task : tasks)
		{
			out.computeIfAbsent(task.getTier(), t -> new ArrayList<>());
			if (matches(task, completed, filter))
			{
				out.get(task.getTier()).add(task);
			}
		}
		Comparator<CombatTask> comparator = comparator(rates, sort);
		for (List<CombatTask> list : out.values())
		{
			list.sort(comparator);
		}
		return out;
	}

	static boolean matches(CombatTask task, Set<Integer> completed, Filter filter)
	{
		if (filter == Filter.INCOMPLETE)
		{
			return !completed.contains(task.getId());
		}
		return true;
	}

	static Comparator<CombatTask> comparator(CompletionRates rates, SortMode sort)
	{
		Comparator<CombatTask> byName = Comparator.comparing(CombatTask::getName, String.CASE_INSENSITIVE_ORDER);
		Comparator<CombatTask> byRateDesc = Comparator.comparingDouble((CombatTask t) ->
		{
			Double r = rates.rateFor(t.getId());
			return r == null ? 1 : -r;
		});
		Comparator<CombatTask> byRateAsc = Comparator.comparingDouble((CombatTask t) ->
		{
			Double r = rates.rateFor(t.getId());
			return r == null ? Double.MAX_VALUE : r;
		});

		switch (sort)
		{
			case WIKI_RATE_ASC:
				return byRateAsc.thenComparing(byName);
			case NAME:
				return byName;
			case WIKI_RATE:
			default:
				return byRateDesc.thenComparing(byName);
		}
	}

	/** @return how many of the tasks are completed */
	static int countDone(List<CombatTask> tasks, Set<Integer> completed)
	{
		int n = 0;
		for (CombatTask task : tasks)
		{
			if (completed.contains(task.getId()))
			{
				n++;
			}
		}
		return n;
	}

	/** @return points earned from the completed tasks in the list */
	static int pointsDone(List<CombatTask> tasks, Set<Integer> completed)
	{
		int n = 0;
		for (CombatTask task : tasks)
		{
			if (completed.contains(task.getId()))
			{
				n += task.getTier().getPoints();
			}
		}
		return n;
	}

	/** @return total points available from the list */
	static int pointsTotal(List<CombatTask> tasks)
	{
		int n = 0;
		for (CombatTask task : tasks)
		{
			n += task.getTier().getPoints();
		}
		return n;
	}
}
