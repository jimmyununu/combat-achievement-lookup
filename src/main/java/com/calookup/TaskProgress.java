package com.calookup;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's own standing against a task's target, shown on the row: "you: 61 / 150" for
 * kill-count tasks and "under 1:20, your best 1:12" for speed tasks. Built from RuneLite's
 * Chat Commands data, which the client already keeps per character.
 */
final class TaskProgress
{
	private static final Pattern TIMES = Pattern.compile("(\\d[\\d,]*)\\s+times", Pattern.CASE_INSENSITIVE);
	private static final Pattern MIN_SEC = Pattern.compile("(\\d+)\\s*min(?:ute)?s?(?:\\s*(?:and\\s*)?(\\d+)\\s*sec(?:ond)?s?)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern SCALE = Pattern.compile("\\((\\d+)-scale\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern GROUP_OF = Pattern.compile("(?:group|team) of (\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SEC_ONLY = Pattern.compile("(\\d+)\\s*seconds?", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLOCK = Pattern.compile("(\\d+):(\\d\\d)(?:\\.(\\d+))?");

	private final String text;
	private final boolean met;

	TaskProgress(String text, boolean met)
	{
		this.text = text;
		this.met = met;
	}

	/** Short text appended to the row's type line. */
	String getText()
	{
		return text;
	}

	/** True when the player's number already satisfies the task's target. */
	boolean isMet()
	{
		return met;
	}

	/**
	 * @return the number of kills a kill-count task asks for, or -1 if the text has none.
	 * "once" and "twice" count; "Kill X 150 times" gives 150.
	 */
	static int parseKillTarget(String description)
	{
		if (description == null)
		{
			return -1;
		}
		Matcher m = TIMES.matcher(description);
		if (m.find())
		{
			try
			{
				return Integer.parseInt(m.group(1).replace(",", ""));
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		String lower = description.toLowerCase(Locale.ROOT);
		if (lower.contains(" once"))
		{
			return 1;
		}
		if (lower.contains(" twice"))
		{
			return 2;
		}
		return -1;
	}

	/**
	 * @return the time limit of a speed task in seconds, or -1 if the text has none.
	 * Understands "1 minute 20 seconds", "54 seconds", "20 minutes" and "2:30".
	 */
	static double parseSpeedTargetSeconds(String description)
	{
		if (description == null)
		{
			return -1;
		}
		Matcher clock = CLOCK.matcher(description);
		if (clock.find())
		{
			return Integer.parseInt(clock.group(1)) * 60 + Integer.parseInt(clock.group(2));
		}
		Matcher ms = MIN_SEC.matcher(description);
		if (ms.find())
		{
			int minutes = Integer.parseInt(ms.group(1));
			int seconds = ms.group(2) == null ? 0 : Integer.parseInt(ms.group(2));
			return minutes * 60 + seconds;
		}
		Matcher s = SEC_ONLY.matcher(description);
		if (s.find())
		{
			return Integer.parseInt(s.group(1));
		}
		return -1;
	}

	/**
	 * The team size a raid task is specific to, in the spelling Chat Commands uses for its
	 * per-size personal bests: {@code solo}, {@code 2 players}, {@code 5 players}. Null when
	 * the task does not name a size ("at any group size", or not a raid), in which case the
	 * boss's overall best applies.
	 */
	static String parseTeamSize(String description)
	{
		if (description == null)
		{
			return null;
		}
		String lower = description.toLowerCase(Locale.ROOT);
		if (lower.contains("(solo)"))
		{
			return "solo";
		}
		if (lower.contains("(duo)"))
		{
			return "2 players";
		}
		if (lower.contains("(trio)"))
		{
			return "3 players";
		}
		Matcher scale = SCALE.matcher(description);
		if (scale.find())
		{
			return players(Integer.parseInt(scale.group(1)));
		}
		Matcher group = GROUP_OF.matcher(description);
		if (group.find())
		{
			return players(Integer.parseInt(group.group(1)));
		}
		return null;
	}

	private static String players(int n)
	{
		return n == 1 ? "solo" : n + " players";
	}

	/** Formats seconds as m:ss, keeping tenths when the value has them (the client stores 0.6 s ticks). */
	static String formatTime(double seconds)
	{
		int whole = (int) Math.floor(seconds);
		double frac = seconds - whole;
		int minutes = whole / 60;
		int secs = whole % 60;
		if (frac >= 0.05)
		{
			return String.format(Locale.ROOT, "%d:%02d.%d", minutes, secs, (int) Math.round(frac * 10) % 10);
		}
		return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
	}

	static TaskProgress forKillCount(int have, int target)
	{
		return new TaskProgress("Your kills: " + have + " / " + target, have >= target);
	}

	static TaskProgress forSpeed(double bestSeconds, double targetSeconds)
	{
		return new TaskProgress("Under " + formatTime(targetSeconds) + "  ·  your best " + formatTime(bestSeconds),
			bestSeconds < targetSeconds);
	}
}
