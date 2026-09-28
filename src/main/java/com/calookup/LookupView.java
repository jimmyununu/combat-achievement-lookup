package com.calookup;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the panel needs to show one boss: an immutable snapshot built on the client
 * thread and handed to the Swing thread.
 */
final class LookupView
{
	private final String boss;
	private final List<String> alternatives;
	private final List<CombatTask> tasks;
	private final Set<Integer> completed;
	private final boolean loggedIn;
	private final Map<Integer, TaskProgress> progress;
	private final String pointsLine;

	LookupView(String boss, List<String> alternatives, List<CombatTask> tasks, Set<Integer> completed, boolean loggedIn)
	{
		this(boss, alternatives, tasks, completed, loggedIn, Collections.emptyMap(), null);
	}

	LookupView(String boss, List<String> alternatives, List<CombatTask> tasks, Set<Integer> completed, boolean loggedIn,
		Map<Integer, TaskProgress> progress, String pointsLine)
	{
		this.boss = boss;
		this.alternatives = alternatives == null ? Collections.emptyList() : Collections.unmodifiableList(alternatives);
		this.tasks = Collections.unmodifiableList(tasks);
		this.completed = Collections.unmodifiableSet(completed);
		this.loggedIn = loggedIn;
		this.progress = progress == null ? Collections.emptyMap() : Collections.unmodifiableMap(progress);
		this.pointsLine = pointsLine;
	}

	/** Display name of the boss group being shown. */
	String getBoss()
	{
		return boss;
	}

	/**
	 * Other boss groups the same NPC belongs to (for example a raid's normal and challenge
	 * modes), including {@link #getBoss()} itself. Empty when there is only one.
	 */
	List<String> getAlternatives()
	{
		return alternatives;
	}

	List<CombatTask> getTasks()
	{
		return tasks;
	}

	Set<Integer> getCompleted()
	{
		return completed;
	}

	/** False when the player is logged out, in which case completion is unknown. */
	boolean isLoggedIn()
	{
		return loggedIn;
	}

	/** Kill-count and PB standing per task, where known. */
	Map<Integer, TaskProgress> getProgress()
	{
		return progress;
	}

	/** "312 points · 8 more for Hard", or null when unknown. */
	String getPointsLine()
	{
		return pointsLine;
	}

	LookupView withCompletion(Set<Integer> newCompleted, boolean newLoggedIn, Map<Integer, TaskProgress> newProgress,
		String newPointsLine)
	{
		return new LookupView(boss, alternatives, tasks, newCompleted, newLoggedIn, newProgress, newPointsLine);
	}
}
