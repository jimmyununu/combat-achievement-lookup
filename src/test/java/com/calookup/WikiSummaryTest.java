package com.calookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WikiSummaryTest
{
	private static final String EXTRACT = "Noxious Foe is an easy combat achievement which requires the player to kill an aberrant spectre.\n"
		+ "This achievement requires 60 Slayer to complete. Boosts can be used.\n\n\n"
		+ "== Strategy ==\n"
		+ "Equipping a nose peg or slayer helmet is required when fighting aberrant spectres.\n\n\n"
		+ "== Other tasks ==\n"
		+ "There are 5 Combat Achievement tasks available.\n\n\n"
		+ "== Trivia ==\n"
		+ "The task's title is a pun.";

	@Test
	public void keepsIntroAndStrategyDropsTheRest()
	{
		WikiSummary s = WikiSummary.parse(EXTRACT);
		assertTrue(s.getIntro().startsWith("Noxious Foe is an easy combat achievement"));
		assertTrue(s.getIntro().endsWith("Boosts can be used."));
		assertEquals("Equipping a nose peg or slayer helmet is required when fighting aberrant spectres.", s.getStrategy());
		assertFalse(s.getStrategy().contains("Trivia"));
		assertFalse(s.getIntro().contains("Other tasks"));
	}

	@Test
	public void pageWithoutHeadingsIsAllIntro()
	{
		WikiSummary s = WikiSummary.parse("Sit Rat is an easy combat achievement.");
		assertEquals("Sit Rat is an easy combat achievement.", s.getIntro());
		assertEquals("", s.getStrategy());
	}

	@Test
	public void emptyExtractIsEmpty()
	{
		assertTrue(WikiSummary.parse("").isEmpty());
		assertTrue(WikiSummary.parse(null).isEmpty());
		assertTrue(WikiSummary.parse("\n\n== Trivia ==\nNothing useful.").isEmpty());
	}

	@Test
	public void truncateStopsAtASentence()
	{
		String text = "First sentence here. Second sentence here. Third sentence is long enough to be cut off.";
		String cut = WikiSummary.truncate(text, 50);
		assertEquals("First sentence here. Second sentence here. …", cut);
		assertEquals(text, WikiSummary.truncate(text, 500));
	}

	@Test
	public void truncateWithoutSentencesFallsBackToWords()
	{
		String text = "word word word word word word word word word word";
		String cut = WikiSummary.truncate(text, 22);
		assertTrue(cut.endsWith("…"));
		assertTrue(cut.length() <= 23);
	}
}
