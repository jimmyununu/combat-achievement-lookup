package com.calookup;

/**
 * The category a Combat Achievement task belongs to, as stored in the task's struct.
 */
public enum TaskType
{
	STAMINA(1, "Stamina"),
	PERFECTION(2, "Perfection"),
	KILL_COUNT(3, "Kill Count"),
	MECHANICAL(4, "Mechanical"),
	RESTRICTION(5, "Restriction"),
	SPEED(6, "Speed"),
	UNKNOWN(-1, "Other");

	private final int id;
	private final String label;

	TaskType(int id, String label)
	{
		this.id = id;
		this.label = label;
	}

	public int getId()
	{
		return id;
	}

	public String getLabel()
	{
		return label;
	}

	public static TaskType fromId(int id)
	{
		for (TaskType type : values())
		{
			if (type.id == id)
			{
				return type;
			}
		}
		return UNKNOWN;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
