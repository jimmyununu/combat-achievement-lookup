package com.calookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Reads the Combat Achievement task list out of the game cache and the player's completion
 * bits out of their varps.
 * <p>
 * Every method that touches {@link Client} must be called on the client thread. The results
 * are immutable snapshots that may then be handed to any thread.
 */
@Slf4j
class CombatTaskRepository
{
	/** Struct param: the task's numeric ID. */
	static final int PARAM_TASK_ID = 1306;
	/** Struct param: the task's title. */
	static final int PARAM_TASK_NAME = 1308;
	/** Struct param: the task's in-game description. */
	static final int PARAM_TASK_DESCRIPTION = 1309;
	/** Struct param: the task type (see {@link TaskType}). */
	static final int PARAM_TASK_TYPE = 1311;
	/** Struct param: index into the boss-name enum. */
	static final int PARAM_TASK_BOSS = 1312;
	/** Enum mapping boss index to boss display name. */
	static final int ENUM_BOSS_NAMES = 3971;

	/** Name the game uses for tasks that are not tied to a boss. */
	static final String GENERAL_BOSS = "None";

	/**
	 * Each varp holds 32 completion bits. Task {@code n} is bit {@code n % 32} of
	 * {@code COMPLETED_VARPS[n / 32]}, exactly as the game's own interface reads them.
	 */
	static final int[] COMPLETED_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1,
		VarPlayerID.CA_TASK_COMPLETED_2, VarPlayerID.CA_TASK_COMPLETED_3,
		VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7,
		VarPlayerID.CA_TASK_COMPLETED_8, VarPlayerID.CA_TASK_COMPLETED_9,
		VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13,
		VarPlayerID.CA_TASK_COMPLETED_14, VarPlayerID.CA_TASK_COMPLETED_15,
		VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19,
		VarPlayerID.CA_TASK_COMPLETED_20,
	};

	private static final Set<Integer> COMPLETED_VARP_SET = new HashSet<>();

	static
	{
		for (int varp : COMPLETED_VARPS)
		{
			COMPLETED_VARP_SET.add(varp);
		}
	}

	private final Client client;

	/** All tasks, in tier order then cache order. Replaced wholesale, never mutated. */
	private volatile List<CombatTask> tasks = Collections.emptyList();

	/** Tasks grouped by normalised boss name. */
	private volatile Map<String, List<CombatTask>> byBoss = Collections.emptyMap();

	/** Display names of every boss group, sorted alphabetically. */
	private volatile List<String> bossNames = Collections.emptyList();

	CombatTaskRepository(Client client)
	{
		this.client = client;
	}

	/** @return true if the given varp is one of the Combat Achievement completion varps */
	static boolean isCompletionVarp(int varpId)
	{
		return COMPLETED_VARP_SET.contains(varpId);
	}

	boolean isLoaded()
	{
		return !tasks.isEmpty();
	}

	List<CombatTask> getTasks()
	{
		return tasks;
	}

	List<String> getBossNames()
	{
		return bossNames;
	}

	/**
	 * @param boss a boss display name, in any capitalisation
	 * @return the tasks filed under that boss, or an empty list
	 */
	List<CombatTask> tasksFor(String boss)
	{
		List<CombatTask> list = byBoss.get(BossMatcher.normalize(boss));
		return list == null ? Collections.emptyList() : list;
	}

	/** @return the display name of the boss group matching the given name, or null */
	String resolveBoss(String boss)
	{
		String key = BossMatcher.normalize(boss);
		List<CombatTask> list = byBoss.get(key);
		if (list == null || list.isEmpty())
		{
			return null;
		}
		return list.get(0).getBoss();
	}

	/**
	 * Reads every task struct out of the cache. Must run on the client thread.
	 *
	 * @return true if at least one task was loaded
	 */
	boolean load()
	{
		List<CombatTask> loaded = new ArrayList<>();
		EnumComposition bossEnum = client.getEnum(ENUM_BOSS_NAMES);

		for (TaskTier tier : TaskTier.values())
		{
			EnumComposition tierEnum = client.getEnum(tier.getEnumId());
			if (tierEnum == null)
			{
				log.debug("No enum {} for tier {}", tier.getEnumId(), tier);
				continue;
			}
			for (int structId : tierEnum.getIntVals())
			{
				StructComposition struct = client.getStructComposition(structId);
				if (struct == null)
				{
					continue;
				}
				int id = struct.getIntValue(PARAM_TASK_ID);
				String name = struct.getStringValue(PARAM_TASK_NAME);
				String description = struct.getStringValue(PARAM_TASK_DESCRIPTION);
				TaskType type = TaskType.fromId(struct.getIntValue(PARAM_TASK_TYPE));
				int bossIndex = struct.getIntValue(PARAM_TASK_BOSS);
				String boss = bossEnum == null ? null : bossEnum.getStringValue(bossIndex);
				if (boss == null || boss.isEmpty())
				{
					boss = GENERAL_BOSS;
				}
				loaded.add(new CombatTask(id, name, description, tier, type, boss));
			}
		}

		if (loaded.isEmpty())
		{
			return false;
		}

		Map<String, List<CombatTask>> grouped = new LinkedHashMap<>();
		Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (CombatTask task : loaded)
		{
			grouped.computeIfAbsent(BossMatcher.normalize(task.getBoss()), k -> new ArrayList<>()).add(task);
			names.add(task.getBoss());
		}
		for (Map.Entry<String, List<CombatTask>> entry : grouped.entrySet())
		{
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}

		tasks = Collections.unmodifiableList(loaded);
		byBoss = Collections.unmodifiableMap(grouped);
		bossNames = Collections.unmodifiableList(new ArrayList<>(names));
		log.debug("Loaded {} combat achievement tasks across {} bosses", loaded.size(), names.size());
		return true;
	}

	/**
	 * Reads the completion bit of every task. Must run on the client thread.
	 *
	 * @return the IDs of every completed task
	 */
	Set<Integer> readCompleted()
	{
		int[] values = new int[COMPLETED_VARPS.length];
		for (int i = 0; i < COMPLETED_VARPS.length; i++)
		{
			values[i] = client.getVarpValue(COMPLETED_VARPS[i]);
		}

		Set<Integer> completed = new HashSet<>();
		for (CombatTask task : tasks)
		{
			if (isCompleted(values, task.getId()))
			{
				completed.add(task.getId());
			}
		}
		return Collections.unmodifiableSet(completed);
	}

	/**
	 * Varbits holding the points needed for each tier's rewards, Easy to Grandmaster. The same
	 * values the in-game interface shows; read from the client rather than hard-coded so a
	 * rebalance needs no plugin update.
	 */
	static final int[] THRESHOLD_VARBITS = {4132, 10660, 10661, 14812, 14813, 14814};

	/** Total points earned from the given completed tasks. */
	int pointsEarned(Set<Integer> completed)
	{
		int points = 0;
		for (CombatTask task : tasks)
		{
			if (completed.contains(task.getId()))
			{
				points += task.getTier().getPoints();
			}
		}
		return points;
	}

	/**
	 * "312 points · 8 more for Hard", or null when the thresholds are not available.
	 * Must run on the client thread.
	 */
	String pointsLine(Set<Integer> completed)
	{
		int[] thresholds = new int[THRESHOLD_VARBITS.length];
		for (int i = 0; i < thresholds.length; i++)
		{
			thresholds[i] = client.getVarbitValue(THRESHOLD_VARBITS[i]);
			if (thresholds[i] <= 0)
			{
				return null;
			}
		}
		return pointsLine(pointsEarned(completed), thresholds);
	}

	/** Pure formatting, split out so it can be unit tested without a client. */
	static String pointsLine(int points, int[] thresholds)
	{
		TaskTier[] tiers = TaskTier.values();
		for (int i = 0; i < thresholds.length && i < tiers.length; i++)
		{
			if (points < thresholds[i])
			{
				int more = thresholds[i] - points;
				return points + (points == 1 ? " point" : " points") + "  ·  " + more + " more for " + tiers[i].getDisplayName();
			}
		}
		return points + " points  ·  Grandmaster reached";
	}

	/** Pure bit test, split out so it can be unit tested without a client. */
	static boolean isCompleted(int[] varpValues, int taskId)
	{
		if (taskId < 0)
		{
			return false;
		}
		int index = taskId / 32;
		if (index >= varpValues.length)
		{
			return false;
		}
		return (varpValues[index] & (1 << (taskId % 32))) != 0;
	}

	void clear()
	{
		tasks = Collections.emptyList();
		byBoss = Collections.emptyMap();
		bossNames = Collections.emptyList();
	}
}
