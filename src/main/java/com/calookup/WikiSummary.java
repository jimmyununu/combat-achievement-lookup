package com.calookup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A short, plain-text explanation of a task built from the wiki's page extract: the opening
 * paragraph plus the strategy section, with trivia and other chatter dropped.
 */
public final class WikiSummary
{
	/** Sections kept from the wiki page, lower-cased. */
	private static final Set<String> KEEP_SECTIONS = new HashSet<>(Arrays.asList(
		"strategy", "strategies", "guide", "tips", "method", "methods", "mechanics", "requirements",
		"notes", "how to complete", "walkthrough"));

	/** Matches {@code == Heading ==} lines produced by exsectionformat=wiki. */
	private static final Pattern HEADING = Pattern.compile("(?m)^\\s*(={2,})\\s*(.+?)\\s*\\1\\s*$");

	public static final int MAX_INTRO_CHARS = 700;
	public static final int MAX_STRATEGY_CHARS = 1100;

	private final String intro;
	private final String strategy;

	WikiSummary(String intro, String strategy)
	{
		this.intro = intro == null ? "" : intro;
		this.strategy = strategy == null ? "" : strategy;
	}

	/** The page's opening paragraph(s), already trimmed to a readable length. */
	public String getIntro()
	{
		return intro;
	}

	/** The strategy/tips text, or empty if the page has none. */
	public String getStrategy()
	{
		return strategy;
	}

	public boolean isEmpty()
	{
		return intro.isEmpty() && strategy.isEmpty();
	}

	/**
	 * Parses the plain-text extract returned by the wiki's TextExtracts API with
	 * {@code explaintext=1&exsectionformat=wiki}.
	 */
	public static WikiSummary parse(String extract)
	{
		if (extract == null || extract.trim().isEmpty())
		{
			return new WikiSummary("", "");
		}

		String text = extract.replace("\r\n", "\n");
		Matcher m = HEADING.matcher(text);

		String intro;
		StringBuilder strategy = new StringBuilder();

		int firstHeading = -1;
		int lastEnd = 0;
		String currentTitle = null;
		int currentStart = 0;
		while (m.find())
		{
			if (firstHeading < 0)
			{
				firstHeading = m.start();
			}
			else
			{
				appendSection(strategy, currentTitle, text.substring(currentStart, m.start()));
			}
			currentTitle = m.group(2);
			currentStart = m.end();
			lastEnd = m.end();
		}
		if (firstHeading < 0)
		{
			intro = text;
		}
		else
		{
			intro = text.substring(0, firstHeading);
			appendSection(strategy, currentTitle, text.substring(lastEnd));
		}

		return new WikiSummary(
			truncate(clean(intro), MAX_INTRO_CHARS),
			truncate(clean(strategy.toString()), MAX_STRATEGY_CHARS));
	}

	private static void appendSection(StringBuilder out, String title, String body)
	{
		if (title == null)
		{
			return;
		}
		if (!KEEP_SECTIONS.contains(title.trim().toLowerCase(Locale.ROOT)))
		{
			return;
		}
		String cleaned = clean(body);
		if (cleaned.isEmpty())
		{
			return;
		}
		if (out.length() > 0)
		{
			out.append("\n\n");
		}
		out.append(cleaned);
	}

	/** Collapses blank lines and surrounding whitespace. */
	static String clean(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replaceAll("[ \\t]+\\n", "\n")
			.replaceAll("\\n{3,}", "\n\n")
			.trim();
	}

	/**
	 * Cuts text to at most {@code max} characters, preferring to stop at the end of a sentence
	 * and adding an ellipsis when something was dropped.
	 */
	static String truncate(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		if (s.length() <= max)
		{
			return s;
		}
		String head = s.substring(0, max);
		int sentence = Math.max(head.lastIndexOf(". "), head.lastIndexOf(".\n"));
		int line = head.lastIndexOf('\n');
		int cut;
		if (sentence >= max / 2)
		{
			cut = sentence + 1;
		}
		else if (line >= max / 2)
		{
			cut = line;
		}
		else
		{
			cut = head.lastIndexOf(' ');
			if (cut <= 0)
			{
				cut = max;
			}
		}
		String out = s.substring(0, cut).trim();
		if (out.endsWith("."))
		{
			return out + " …";
		}
		return out + "…";
	}
}
