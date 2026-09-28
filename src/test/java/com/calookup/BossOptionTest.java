package com.calookup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BossOptionTest
{
	private static final CombatTask Z1 = new CombatTask(1, "Z easy", "", TaskTier.EASY, TaskType.KILL_COUNT, "Zulrah");
	private static final CombatTask Z2 = new CombatTask(2, "Z master", "", TaskTier.MASTER, TaskType.SPEED, "Zulrah");
	private static final CombatTask V1 = new CombatTask(3, "V hard", "", TaskTier.HARD, TaskType.SPEED, "Vorkath");
	private static final CombatTask A1 = new CombatTask(4, "A easy", "", TaskTier.EASY, TaskType.KILL_COUNT, "Araxxor");

	private static List<BossOption> build(Set<Integer> completed, boolean loggedIn)
	{
		List<String> bosses = Arrays.asList("Araxxor", "Vorkath", "Zulrah");
		List<List<CombatTask>> tasksOf = Arrays.asList(
			Collections.singletonList(A1), Collections.singletonList(V1), Arrays.asList(Z1, Z2));
		return BossOption.build(bosses, tasksOf, Arrays.asList(A1, V1, Z1, Z2), completed, loggedIn);
	}

	@Test
	public void allComesFirstThenMostPointsLeft()
	{
		List<BossOption> options = build(new HashSet<>(), true);
		assertEquals(BossOption.ALL, options.get(0).getBoss());
		assertEquals(10, options.get(0).getTotalPoints());
		assertEquals("Zulrah", options.get(1).getBoss());   // 6 points
		assertEquals("Vorkath", options.get(2).getBoss());  // 3
		assertEquals("Araxxor", options.get(3).getBoss());  // 1
	}

	@Test
	public void completedPointsDropABossDownTheList()
	{
		List<BossOption> options = build(new HashSet<>(Arrays.asList(2)), true);
		assertEquals("Vorkath", options.get(1).getBoss());  // 3 left
		// Araxxor and Zulrah both have 1 left; ties are alphabetical.
		assertEquals("Araxxor", options.get(2).getBoss());
		assertEquals("Zulrah", options.get(3).getBoss());
		assertEquals(1, options.get(3).getRemainingPoints());
		assertEquals("Zulrah  ·  1 pt left", options.get(3).getLabel());
		assertEquals(5, options.get(0).getRemainingPoints());
	}

	@Test
	public void loggedOutUsesTotals()
	{
		List<BossOption> options = build(new HashSet<>(Arrays.asList(2)), false);
		assertEquals("Zulrah", options.get(1).getBoss());
		assertEquals("Zulrah  ·  6 pts", options.get(1).getLabel());
		assertTrue(options.get(0).getLabel().startsWith("(All)"));
	}
}
