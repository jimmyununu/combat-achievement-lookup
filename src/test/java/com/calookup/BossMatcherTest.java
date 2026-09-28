package com.calookup;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BossMatcherTest
{
	@Test
	public void normalizeStripsTagsCaseAndLeadingThe()
	{
		assertEquals("zulrah", BossMatcher.normalize("<col=ffff00>Zulrah</col>"));
		assertEquals("leviathan", BossMatcher.normalize("The Leviathan"));
		assertEquals("vet'ion", BossMatcher.normalize("Vet’ion"));
		assertEquals("chambers of xeric: challenge mode", BossMatcher.normalize("  Chambers  of Xeric: Challenge Mode "));
		assertEquals("", BossMatcher.normalize(null));
	}

	@Test
	public void directNameIsAlwaysACandidate()
	{
		List<String> groups = BossMatcher.groupsFor("Zulrah");
		assertEquals(1, groups.size());
		assertEquals("zulrah", groups.get(0));
	}

	@Test
	public void raidRoomsMapToEveryRaidMode()
	{
		List<String> groups = BossMatcher.groupsFor("Great Olm");
		assertTrue(groups.contains("Chambers of Xeric"));
		assertTrue(groups.contains("Chambers of Xeric: Challenge Mode"));
		assertEquals("Chambers of Xeric", groups.get(0));

		groups = BossMatcher.groupsFor("Verzik Vitur");
		assertTrue(groups.contains("Theatre of Blood"));
		assertTrue(groups.contains("Theatre of Blood: Hard Mode"));
		assertTrue(groups.contains("Theatre of Blood: Entry Mode"));

		groups = BossMatcher.groupsFor("Tumeken's Warden");
		assertTrue(groups.contains("Tombs of Amascut"));
		assertTrue(groups.contains("Tombs of Amascut: Expert Mode"));
	}

	@Test
	public void jadInTheInfernoBelongsToTwoGroups()
	{
		List<String> groups = BossMatcher.groupsFor("JalTok-Jad");
		assertTrue(groups.contains("TzKal-Zuk"));
		assertTrue(groups.contains("TzHaar-Ket-Rak's Challenges"));
	}

	@Test
	public void lesserWildernessFormsMapToTheirBoss()
	{
		assertEquals("Callisto", BossMatcher.aliasesFor("Artio").get(0));
		assertEquals("Venenatis", BossMatcher.aliasesFor("Spindel").get(0));
		assertEquals("Vet'ion", BossMatcher.aliasesFor("Calvar'ion").get(0));
	}

	@Test
	public void unknownNpcHasNoAliases()
	{
		assertTrue(BossMatcher.aliasesFor("Man").isEmpty());
		assertTrue(BossMatcher.groupsFor("").isEmpty());
		assertFalse(BossMatcher.groupsFor("Man").isEmpty());
	}

	@Test
	public void aliasTableUsesConsistentGroupNames()
	{
		// Every aliased group must survive normalisation unchanged apart from case/"the",
		// so it can be resolved against the live task list.
		for (String npc : BossMatcher.allAliasedNpcNames())
		{
			for (String group : BossMatcher.aliasesFor(npc))
			{
				assertFalse("empty group for " + npc, group.trim().isEmpty());
				assertTrue(BossMatcher.sameGroup(group, group.toUpperCase()));
			}
		}
	}
}
