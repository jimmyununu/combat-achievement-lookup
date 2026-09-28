package com.calookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the player's kill counts and personal bests that RuneLite's Chat Commands plugin
 * records per character, and matches them to kill-count and speed tasks.
 * <p>
 * Chat Commands stores kill counts under the {@code killcount} group and personal bests under
 * {@code personalbest}, keyed by the boss name from the game's own message, lower-cased. Most
 * Combat Achievement boss groups use the same name; the exceptions are listed here.
 */
final class ProgressReader
{
	static final String KC_GROUP = "killcount";
	static final String PB_GROUP = "personalbest";

	/** Boss group (normalised) to the config keys Chat Commands might have used, in order of preference. */
	private static final Map<String, List<String>> KEYS = new HashMap<>();

	private static void keys(String boss, String... candidates)
	{
		KEYS.put(BossMatcher.normalize(boss), List.of(candidates));
	}

	static
	{
		keys("Chambers of Xeric", "chambers of xeric");
		keys("Chambers of Xeric: Challenge Mode", "chambers of xeric challenge mode");
		keys("Theatre of Blood", "theatre of blood");
		keys("Theatre of Blood: Hard Mode", "theatre of blood hard mode");
		keys("Theatre of Blood: Entry Mode", "theatre of blood entry mode", "theatre of blood story mode");
		keys("Tombs of Amascut", "tombs of amascut");
		keys("Tombs of Amascut: Expert Mode", "tombs of amascut expert mode");
		keys("Tombs of Amascut: Entry Mode", "tombs of amascut entry mode");
		keys("Crystalline Hunllef", "gauntlet");
		keys("Corrupted Hunllef", "corrupted gauntlet");
		keys("Barrows", "barrows chests");
		keys("Moons of Peril", "lunar chest", "lunar chests");
		keys("Fortis Colosseum", "sol heredit");
		keys("The Nightmare", "nightmare");
		keys("Phosani's Nightmare", "phosani's nightmare");
		keys("The Hueycoatl", "hueycoatl");
		keys("The Mimic", "mimic");
		keys("Leviathan", "leviathan", "the leviathan");
		keys("Whisperer", "whisperer", "the whisperer");
		keys("Royal Titans", "royal titans", "the royal titans");
		keys("TzHaar-Ket-Rak's Challenges", "tzhaar-ket-rak's challenges");
	}

	private final ConfigManager configManager;

	ProgressReader(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** The config keys to try for a boss group, most specific first. */
	static List<String> keysFor(String boss)
	{
		List<String> known = KEYS.get(BossMatcher.normalize(boss));
		if (known != null)
		{
			return known;
		}
		String lower = boss.toLowerCase(Locale.ROOT).trim();
		List<String> out = new ArrayList<>(3);
		out.add(lower);
		if (lower.startsWith("the "))
		{
			out.add(lower.substring(4));
		}
		else
		{
			out.add("the " + lower);
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * @return progress for every task that has a readable target and a recorded number, by task ID
	 */
	Map<Integer, TaskProgress> read(List<CombatTask> tasks)
	{
		Map<Integer, TaskProgress> out = new HashMap<>();
		Map<String, Integer> kcByBoss = new HashMap<>();
		Map<String, Double> pbByBoss = new HashMap<>();
		for (CombatTask task : tasks)
		{
			if (task.getType() == TaskType.KILL_COUNT)
			{
				int target = TaskProgress.parseKillTarget(task.getDescription());
				if (target <= 0)
				{
					continue;
				}
				Integer kc = kcByBoss.computeIfAbsent(task.getBoss(), this::killCount);
				if (kc != null)
				{
					out.put(task.getId(), TaskProgress.forKillCount(kc, target));
				}
			}
			else if (task.getType() == TaskType.SPEED)
			{
				double target = TaskProgress.parseSpeedTargetSeconds(task.getDescription());
				if (target <= 0)
				{
					continue;
				}
				// A "(Duo)" or "(5-scale)" task only counts a best set at that team size; the
				// client records those separately, so never fall back to the overall best.
				String size = TaskProgress.parseTeamSize(task.getDescription());
				String cacheKey = size == null ? task.getBoss() : task.getBoss() + "|" + size;
				Double pb = pbByBoss.computeIfAbsent(cacheKey, k -> personalBest(task.getBoss(), size));
				if (pb != null)
				{
					out.put(task.getId(), TaskProgress.forSpeed(pb, target));
				}
			}
		}
		return out;
	}

	private Integer killCount(String boss)
	{
		for (String key : keysFor(boss))
		{
			Integer kc = configManager.getRSProfileConfiguration(KC_GROUP, key, Integer.class);
			if (kc != null && kc > 0)
			{
				return kc;
			}
		}
		return null;
	}

	/** Config keys for a boss's PB at a team size, e.g. {@code theatre of blood 2 players}. */
	static List<String> pbKeysFor(String boss, String teamSize)
	{
		if (teamSize == null)
		{
			return keysFor(boss);
		}
		List<String> out = new ArrayList<>();
		for (String key : keysFor(boss))
		{
			out.add(key + " " + teamSize);
		}
		return Collections.unmodifiableList(out);
	}

	private Double personalBest(String boss, String teamSize)
	{
		for (String key : pbKeysFor(boss, teamSize))
		{
			Double pb = configManager.getRSProfileConfiguration(PB_GROUP, key, Double.class);
			if (pb != null && pb > 0)
			{
				return pb;
			}
		}
		return null;
	}
}
