package com.calookup;

import java.util.Objects;

/**
 * One Combat Achievement task as read from the game cache. Immutable and safe to share
 * between the client thread and the Swing thread.
 */
public final class CombatTask
{
	private final int id;
	private final String name;
	private final String description;
	private final TaskTier tier;
	private final TaskType type;
	private final String boss;

	public CombatTask(int id, String name, String description, TaskTier tier, TaskType type, String boss)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.description = description == null ? "" : description;
		this.tier = tier;
		this.type = type == null ? TaskType.UNKNOWN : type;
		this.boss = boss == null ? "" : boss;
	}

	/** The game's task ID. Also the key used by the wiki for completion rates. */
	public int getId()
	{
		return id;
	}

	/** Task title, e.g. {@code Noxious Foe}. Matches the task's page title on the wiki. */
	public String getName()
	{
		return name;
	}

	/** The in-game task text, e.g. {@code Kill an Aberrant Spectre.} */
	public String getDescription()
	{
		return description;
	}

	public TaskTier getTier()
	{
		return tier;
	}

	public TaskType getType()
	{
		return type;
	}

	/** The boss or monster group the game files this task under, e.g. {@code Zulrah}. */
	public String getBoss()
	{
		return boss;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof CombatTask && ((CombatTask) o).id == id;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public String toString()
	{
		return tier + " " + name + " (#" + id + ")";
	}
}
