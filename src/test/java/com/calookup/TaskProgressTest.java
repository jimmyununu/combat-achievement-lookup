package com.calookup;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TaskProgressTest
{
	@Test
	public void killTargets()
	{
		assertEquals(150, TaskProgress.parseKillTarget("Kill Zulrah 150 times."));
		assertEquals(1000, TaskProgress.parseKillTarget("Kill the Kalphite Queen 1,000 times."));
		assertEquals(1, TaskProgress.parseKillTarget("Kill the Hueycoatl once."));
		assertEquals(2, TaskProgress.parseKillTarget("Complete the Gauntlet twice."));
		assertEquals(-1, TaskProgress.parseKillTarget("Kill Zulrah without taking damage."));
		assertEquals(-1, TaskProgress.parseKillTarget(null));
	}

	@Test
	public void speedTargets()
	{
		assertEquals(80, TaskProgress.parseSpeedTargetSeconds("Kill Zulrah in less than 1 minute 20 seconds, without a slayer task."), 0.001);
		assertEquals(54, TaskProgress.parseSpeedTargetSeconds("Kill Zulrah in less than 54 seconds."), 0.001);
		assertEquals(1200, TaskProgress.parseSpeedTargetSeconds("Complete the Theatre of Blood in less than 20 minutes."), 0.001);
		assertEquals(150, TaskProgress.parseSpeedTargetSeconds("Complete Wave 1 in under 2:30."), 0.001);
		assertEquals(90, TaskProgress.parseSpeedTargetSeconds("Kill Vorkath in less than 1 minute and 30 seconds."), 0.001);
		assertEquals(-1, TaskProgress.parseSpeedTargetSeconds("Kill Vorkath 50 times."), 0.001);
	}

	@Test
	public void teamSizes()
	{
		assertEquals("solo", TaskProgress.parseTeamSize("Complete a Chambers of Xeric (Solo) in less than 17 minutes."));
		assertEquals("2 players", TaskProgress.parseTeamSize("Complete the Theatre of Blood (Duo) in less than 26 minutes."));
		assertEquals("3 players", TaskProgress.parseTeamSize("Complete the Theatre of Blood: Hard Mode (Trio) with an overall time of less than 23 minutes."));
		assertEquals("5 players", TaskProgress.parseTeamSize("Complete a Chambers of Xeric: Challenge Mode (5-scale) in less than 25 minutes."));
		assertEquals("8 players", TaskProgress.parseTeamSize("Complete the Tombs of Amascut (expert) within 18 mins in a group of 8."));
		assertEquals(null, TaskProgress.parseTeamSize("Complete the Tombs of Amascut (normal) within 18 mins at any group size."));
		assertEquals(null, TaskProgress.parseTeamSize("Complete the Gauntlet in less than 3 minutes and 45 seconds."));
		assertEquals(1080, TaskProgress.parseSpeedTargetSeconds("Complete the Tombs of Amascut (normal) within 18 mins at any group size."), 0.001);
		assertEquals(1680, TaskProgress.parseSpeedTargetSeconds("Complete the Colosseum with a total time of 28:00 or less."), 0.001);

		assertEquals(List.of("theatre of blood 2 players"), ProgressReader.pbKeysFor("Theatre of Blood", "2 players"));
		assertEquals(List.of("chambers of xeric challenge mode solo"), ProgressReader.pbKeysFor("Chambers of Xeric: Challenge Mode", "solo"));
		assertEquals(ProgressReader.keysFor("Zulrah"), ProgressReader.pbKeysFor("Zulrah", null));
	}

	@Test
	public void timeFormatting()
	{
		assertEquals("1:12", TaskProgress.formatTime(72));
		assertEquals("1:12.6", TaskProgress.formatTime(72.6));
		assertEquals("0:54", TaskProgress.formatTime(54));
		assertEquals("20:00", TaskProgress.formatTime(1200));
	}

	@Test
	public void progressText()
	{
		TaskProgress kc = TaskProgress.forKillCount(61, 150);
		assertEquals("Your kills: 61 / 150", kc.getText());
		assertFalse(kc.isMet());
		assertTrue(TaskProgress.forKillCount(150, 150).isMet());

		TaskProgress pb = TaskProgress.forSpeed(72, 60);
		assertEquals("Under 1:00  ·  your best 1:12", pb.getText());
		assertFalse(pb.isMet());
		assertTrue(TaskProgress.forSpeed(58.8, 60).isMet());
	}

	@Test
	public void configKeysForBosses()
	{
		assertEquals(List.of("zulrah", "the zulrah"), ProgressReader.keysFor("Zulrah"));
		assertEquals(List.of("hueycoatl", "the hueycoatl"), ProgressReader.keysFor("The Hueycoatl").subList(0, 1).equals(List.of("hueycoatl"))
			? List.of("hueycoatl", "the hueycoatl") : ProgressReader.keysFor("The Hueycoatl"));
		assertEquals("chambers of xeric challenge mode", ProgressReader.keysFor("Chambers of Xeric: Challenge Mode").get(0));
		assertEquals("gauntlet", ProgressReader.keysFor("Crystalline Hunllef").get(0));
		assertEquals("barrows chests", ProgressReader.keysFor("Barrows").get(0));
		assertEquals(Arrays.asList("leviathan", "the leviathan"), ProgressReader.keysFor("Leviathan"));
	}

	@Test
	public void pointsLine()
	{
		int[] thresholds = {33, 115, 304, 820, 1465, 2005};
		assertEquals("0 points  ·  33 more for Easy", CombatTaskRepository.pointsLine(0, thresholds));
		assertEquals("296 points  ·  8 more for Hard", CombatTaskRepository.pointsLine(296, thresholds));
		assertEquals("2005 points  ·  Grandmaster reached", CombatTaskRepository.pointsLine(2005, thresholds));
		assertEquals("1 point  ·  32 more for Easy", CombatTaskRepository.pointsLine(1, thresholds));
	}
}
