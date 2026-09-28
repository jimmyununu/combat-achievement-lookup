package com.calookup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TaskArrangerTest
{
	private static final CombatTask EASY_A = new CombatTask(1, "Alpha", "", TaskTier.EASY, TaskType.KILL_COUNT, "Zulrah");
	private static final CombatTask EASY_B = new CombatTask(2, "Bravo", "", TaskTier.EASY, TaskType.MECHANICAL, "Zulrah");
	private static final CombatTask HARD_C = new CombatTask(3, "Charlie", "", TaskTier.HARD, TaskType.SPEED, "Zulrah");
	private static final CombatTask HARD_D = new CombatTask(4, "Delta", "", TaskTier.HARD, TaskType.RESTRICTION, "Zulrah");
	private static final List<CombatTask> TASKS = Arrays.asList(HARD_D, EASY_B, HARD_C, EASY_A);

	private static CompletionRates rates()
	{
		Map<Integer, Double> m = new HashMap<>();
		m.put(1, 40.0);
		m.put(2, 80.0);
		m.put(3, 5.0);
		// 4 has no rate
		return new CompletionRates(m, CompletionRates.Source.SNAPSHOT, null, null);
	}

	@Test
	public void tiersAreKeptInOrderAndSortedByRate()
	{
		Map<TaskTier, List<CombatTask>> out = TaskArranger.arrange(TASKS, new HashSet<>(), rates(),
			TaskArranger.Filter.ALL, TaskArranger.SortMode.WIKI_RATE);
		assertEquals(Arrays.asList(TaskTier.EASY, TaskTier.HARD), new java.util.ArrayList<>(out.keySet()));
		assertEquals(Arrays.asList(EASY_B, EASY_A), out.get(TaskTier.EASY));
		// Unknown rate sorts last.
		assertEquals(Arrays.asList(HARD_C, HARD_D), out.get(TaskTier.HARD));
	}

	@Test
	public void rarestFirstPutsUnknownLast()
	{
		Map<TaskTier, List<CombatTask>> out = TaskArranger.arrange(TASKS, new HashSet<>(), rates(),
			TaskArranger.Filter.ALL, TaskArranger.SortMode.WIKI_RATE_ASC);
		assertEquals(Arrays.asList(EASY_A, EASY_B), out.get(TaskTier.EASY));
		assertEquals(Arrays.asList(HARD_C, HARD_D), out.get(TaskTier.HARD));
	}

	@Test
	public void nameSortIsAlphabetical()
	{
		Map<TaskTier, List<CombatTask>> out = TaskArranger.arrange(TASKS, new HashSet<>(), rates(),
			TaskArranger.Filter.ALL, TaskArranger.SortMode.NAME);
		assertEquals(Arrays.asList(EASY_A, EASY_B), out.get(TaskTier.EASY));
		assertEquals(Arrays.asList(HARD_C, HARD_D), out.get(TaskTier.HARD));
	}

	@Test
	public void incompleteFilterKeepsEmptyTierSections()
	{
		Set<Integer> done = new HashSet<>(Arrays.asList(1, 2));
		Map<TaskTier, List<CombatTask>> out = TaskArranger.arrange(TASKS, done, rates(),
			TaskArranger.Filter.INCOMPLETE, TaskArranger.SortMode.NAME);
		assertTrue(out.containsKey(TaskTier.EASY));
		assertTrue(out.get(TaskTier.EASY).isEmpty());
		assertEquals(Arrays.asList(HARD_C, HARD_D), out.get(TaskTier.HARD));
	}

	@Test
	public void countsAndPoints()
	{
		Set<Integer> done = new HashSet<>(Arrays.asList(1, 3));
		assertEquals(2, TaskArranger.countDone(TASKS, done));
		assertEquals(TaskTier.EASY.getPoints() + TaskTier.HARD.getPoints(), TaskArranger.pointsDone(TASKS, done));
		assertEquals(2 * TaskTier.EASY.getPoints() + 2 * TaskTier.HARD.getPoints(), TaskArranger.pointsTotal(TASKS));
	}

	@Test
	public void comboSearchPrefersPrefixMatches()
	{
		List<String> items = Arrays.asList("Chambers of Xeric", "Zulrah", "Zalcano", "Vorkath", "TzKal-Zuk");
		assertEquals(Arrays.asList("Zulrah", "Zalcano", "TzKal-Zuk"), SearchableComboBox.matching(items, "z"));
		assertEquals(Arrays.asList("Zulrah", "TzKal-Zuk"), SearchableComboBox.matching(items, "ZU"));
		assertEquals(items, SearchableComboBox.matching(items, "  "));
		assertTrue(SearchableComboBox.matching(items, "nope").isEmpty());
	}
}
