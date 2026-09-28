package com.calookup;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LookupPanelTest
{
	@Test
	public void raidModesAreShortenedToTheirSuffix()
	{
		java.util.List<String> all = Arrays.asList("Theatre of Blood", "Theatre of Blood: Entry Mode", "Theatre of Blood: Hard Mode");
		assertEquals("Normal", LookupPanel.shortName("Theatre of Blood", all));
		assertEquals("Entry Mode", LookupPanel.shortName("Theatre of Blood: Entry Mode", all));
		assertEquals("Hard Mode", LookupPanel.shortName("Theatre of Blood: Hard Mode", all));
	}

	@Test
	public void unrelatedGroupsKeepTheirFullNames()
	{
		java.util.List<String> all = Arrays.asList("TzKal-Zuk", "TzHaar-Ket-Rak's Challenges");
		assertEquals("TzKal-Zuk", LookupPanel.shortName("TzKal-Zuk", all));
		assertEquals("TzHaar-Ket-Rak's Challenges", LookupPanel.shortName("TzHaar-Ket-Rak's Challenges", all));
		assertEquals("Zulrah", LookupPanel.shortName("Zulrah", Collections.singletonList("Zulrah")));
	}
}
