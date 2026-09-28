package com.calookup;

/**
 * The six Combat Achievement difficulty tiers, in ascending order.
 * <p>
 * Each tier has an in-game enum that lists the structs of every task in that tier. The enum IDs
 * are the same ones the game's own Combat Achievements interface iterates and are not user
 * supplied.
 */
public enum TaskTier
{
	EASY("Easy", 1, 3981),
	MEDIUM("Medium", 2, 3982),
	HARD("Hard", 3, 3983),
	ELITE("Elite", 4, 3984),
	MASTER("Master", 5, 3985),
	GRANDMASTER("Grandmaster", 6, 3986);

	private final String displayName;
	private final int points;
	private final int enumId;

	TaskTier(String displayName, int points, int enumId)
	{
		this.displayName = displayName;
		this.points = points;
		this.enumId = enumId;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** Combat Achievement points awarded for a task of this tier. */
	public int getPoints()
	{
		return points;
	}

	/** The in-game enum whose int values are the struct IDs of every task in this tier. */
	public int getEnumId()
	{
		return enumId;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
