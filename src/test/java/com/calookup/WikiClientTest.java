package com.calookup;

import com.google.gson.Gson;
import java.io.StringReader;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WikiClientTest
{
	private final Gson gson = new Gson();

	@Test
	public void parsesTheCompletionDocument()
	{
		Map<Integer, Double> rates = WikiClient.parseRates(gson, new StringReader("{\"0\": 69.9, \"1\": 31.3, \"x\": 5, \"7\": null}"));
		assertEquals(2, rates.size());
		assertEquals(69.9, rates.get(0), 0.0001);
		assertEquals(31.3, rates.get(1), 0.0001);
	}

	@Test
	public void parsesAnExtract()
	{
		String json = "{\"batchcomplete\":true,\"query\":{\"pages\":[{\"pageid\":1,\"ns\":0,\"title\":\"Sit Rat\","
			+ "\"extract\":\"Sit Rat is an easy combat achievement.\"}]}}";
		assertEquals("Sit Rat is an easy combat achievement.", WikiClient.parseExtract(gson, json));
	}

	@Test
	public void missingPageIsNull()
	{
		String json = "{\"query\":{\"pages\":[{\"ns\":0,\"title\":\"Nope\",\"missing\":true}]}}";
		assertNull(WikiClient.parseExtract(gson, json));
		assertNull(WikiClient.parseExtract(gson, "{}"));
	}

	@Test
	public void urlsPointAtTheWiki()
	{
		String page = WikiClient.taskPageUrl("You're a wizard").toString();
		assertTrue(page, page.startsWith("https://oldschool.runescape.wiki/w/You're_a_wizard")
			|| page.startsWith("https://oldschool.runescape.wiki/w/You%27re_a_wizard"));
		String spaced = WikiClient.taskPageUrl("Noxious Foe").toString();
		assertTrue(spaced, spaced.startsWith("https://oldschool.runescape.wiki/w/Noxious_Foe"));

		String api = WikiClient.extractUrl("Noxious Foe").toString();
		assertTrue(api, api.startsWith("https://oldschool.runescape.wiki/api.php?"));
		assertTrue(api, api.contains("titles=Noxious%20Foe"));
		assertTrue(api, api.contains("prop=extracts"));
	}

	@Test
	public void bundledSnapshotLoads()
	{
		CompletionRates snapshot = CompletionRates.loadSnapshot(gson);
		assertEquals(CompletionRates.Source.SNAPSHOT, snapshot.getSource());
		assertTrue("snapshot should hold every task", snapshot.size() >= 600);
		assertEquals(69.9, snapshot.rateFor(0), 0.0001);
		assertNull(snapshot.rateFor(999999));
	}
}
