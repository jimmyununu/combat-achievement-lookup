package com.calookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.util.Text;

/**
 * Maps the name of an NPC the player right-clicked to the Combat Achievement boss group(s)
 * its tasks are filed under.
 * <p>
 * Most bosses are filed under their own name, so the fallback is a direct name match. This
 * table covers the rest: raid rooms, minions, forms, brothers and the odd renamed group. NPC
 * names are matched after {@link #normalize(String)}, so capitalisation, colour tags and a
 * leading "The" do not matter. Nothing here is a user-supplied ID.
 */
final class BossMatcher
{
	private static final Map<String, List<String>> ALIASES = new HashMap<>();

	private static void alias(String group, String... npcNames)
	{
		for (String npc : npcNames)
		{
			ALIASES.computeIfAbsent(normalize(npc), k -> new ArrayList<>()).add(group);
		}
	}

	private static void aliasAll(String[] groups, String... npcNames)
	{
		for (String group : groups)
		{
			alias(group, npcNames);
		}
	}

	static
	{
		// Chambers of Xeric
		String[] cox = {"Chambers of Xeric", "Chambers of Xeric: Challenge Mode"};
		aliasAll(cox, "Great Olm", "Great Olm (Left claw)", "Great Olm (Right claw)", "Tekton", "Tekton (enraged)",
			"Vasa Nistirio", "Vanguard", "Vespula", "Vespine soldier", "Muttadile", "Ice demon", "Skeletal Mystic",
			"Abyssal portal", "Deathly ranger", "Deathly mage", "Scavenger beast", "Jewelled Crab",
			"Lux grub", "Guardian");
		aliasAll(cox, "Lizardman shaman");

		// Theatre of Blood
		String[] tob = {"Theatre of Blood", "Theatre of Blood: Entry Mode", "Theatre of Blood: Hard Mode"};
		aliasAll(tob, "The Maiden of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias", "Nylocas Prinkipas",
			"Nylocas Ischyros", "Nylocas Toxobolos", "Nylocas Hagios", "Sotetseg", "Xarpus", "Verzik Vitur",
			"Nylocas Matomenos", "Blood spawn");

		// Tombs of Amascut
		String[] toa = {"Tombs of Amascut", "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Expert Mode"};
		aliasAll(toa, "Ba-Ba", "Akkha", "Akkha's Shadow", "Kephri", "Zebak", "Tumeken's Warden", "Elidinis' Warden",
			"Obelisk", "Scarab Swarm", "Soldier Scarab", "Spitting Scarab", "Arcane Scarab", "Baboon Brawler",
			"Baboon Thrower", "Baboon Mage", "Baboon Shaman", "Volatile Baboon", "Cursed Baboon", "Crocodile");

		// Inferno, Fight Caves and TzHaar-Ket-Rak's Challenges
		alias("TzKal-Zuk", "TzKal-Zuk", "Jal-Zek", "Jal-Xil", "Jal-ImKot", "Jal-AkRek-Mej", "Jal-AkRek-Ket",
			"Jal-AkRek-Xil", "Jal-MejRah", "Jal-Nib", "Jal-Ak", "Jal-MejJak", "Ancestral Glyph", "Rocky support");
		alias("TzTok-Jad", "TzTok-Jad", "Tz-Kih", "Tz-Kek", "Tok-Xil", "Yt-MejKot", "Ket-Zek", "Yt-HurKot");
		aliasAll(new String[]{"TzHaar-Ket-Rak's Challenges", "TzKal-Zuk"}, "JalTok-Jad");
		alias("TzHaar-Ket-Rak's Challenges", "TzHaar-Ket-Rak");

		// Gauntlet
		alias("Crystalline Hunllef", "Crystalline Hunllef");
		alias("Corrupted Hunllef", "Corrupted Hunllef");

		// Barrows
		alias("Barrows", "Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested", "Karil the Tainted",
			"Torag the Corrupted", "Verac the Defiled");

		// Grotesque Guardians
		alias("Grotesque Guardians", "Dusk", "Dawn");

		// God Wars Dungeon
		alias("Commander Zilyana", "Starlight", "Bree", "Growler");
		alias("General Graardor", "Sergeant Strongstack", "Sergeant Steelwill", "Sergeant Grimspike");
		alias("K'ril Tsutsaroth", "Tstanon Karlak", "Zakl'n Gritch", "Balfrug Kreeyath");
		alias("Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree");
		alias("Nex", "Fumus", "Umbra", "Cruor", "Glacies");

		// Wilderness bosses and their lesser forms
		alias("Callisto", "Artio");
		alias("Venenatis", "Spindel");
		alias("Vet'ion", "Calvar'ion", "Vet'ion Reborn", "Calvar'ion Reborn", "Skeleton Hellhound",
			"Greater Skeleton Hellhound");
		alias("Corporeal Beast", "Dark energy core");

		// Nightmare
		alias("The Nightmare", "The Nightmare", "Parasite", "Husk", "Sleepwalker", "Totem");
		alias("Phosani's Nightmare", "Phosani's Nightmare");

		// Desert Treasure II
		alias("Leviathan", "The Leviathan");
		alias("Whisperer", "The Whisperer");

		// Moons of Peril
		alias("Moons of Peril", "Blood Moon", "Blue Moon", "Eclipse Moon");

		// Royal Titans
		alias("Royal Titans", "Branda the Fire Queen", "Eldric the Ice King");

		// Fortis Colosseum
		alias("Fortis Colosseum", "Sol Heredit", "Fremennik warband archer", "Fremennik warband berserker",
			"Fremennik warband seer", "Serpent shaman", "Jaguar warrior", "Javelin Colossus", "Manticore",
			"Shockwave Colossus", "Minotaur", "Minimus");

		// Slayer monsters with variant names
		alias("Bloodveld", "Mutated Bloodveld");
		alias("Gargoyle", "Marble gargoyle");
		alias("Kurask", "King kurask");
		alias("Black Dragon", "Black dragon");
		alias("Wyrm", "Wyrmling");
		alias("Kraken", "Whirlpool");
		alias("Alchemical Hydra", "Alchemical Hydra");
		alias("Hespori", "Flower");
		alias("Zalcano", "Zalcano");
		alias("Tempoross", "Tempoross", "Spirit pool");
		alias("Wintertodt", "Wintertodt", "Incapacitated Wintertodt");
		alias("The Hueycoatl", "Hueycoatl");
		alias("The Mimic", "Mimic");
		alias("Scurrius", "Giant rat");
		alias("Duke Sucellus", "Duke Sucellus");
		alias("Vardorvis", "Vardorvis");
		alias("Yama", "Judge of Yama", "Yama");

		// The giant cave tasks
		alias("Giants", "Hill Giant", "Moss giant", "Fire giant");
	}

	private BossMatcher()
	{
	}

	/**
	 * Lower-cases, strips colour tags, unifies apostrophes, collapses whitespace and drops a
	 * leading "the". Applied to both NPC names and boss group names before comparing.
	 */
	static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}
		String n = Text.removeTags(name)
			.replace('’', '\'')
			.replace('‘', '\'')
			.toLowerCase(Locale.ROOT)
			.trim()
			.replaceAll("\\s+", " ");
		if (n.startsWith("the "))
		{
			n = n.substring(4);
		}
		return n;
	}

	/**
	 * @param npcName the NPC's name as shown in the menu (tags allowed)
	 * @return the boss group names this NPC belongs to, in priority order. Empty if none.
	 * The names are as written in the alias table; callers should resolve them against the
	 * live task list with {@link BossMatcher#normalize(String)} so a renamed group still matches.
	 */
	static List<String> groupsFor(String npcName)
	{
		String key = normalize(npcName);
		if (key.isEmpty())
		{
			return Collections.emptyList();
		}
		Set<String> groups = new LinkedHashSet<>();
		List<String> aliased = ALIASES.get(key);
		if (aliased != null)
		{
			groups.addAll(aliased);
		}
		// Direct match: most bosses are filed under their own name.
		groups.add(key);
		return Collections.unmodifiableList(new ArrayList<>(groups));
	}

	/** Only the explicitly aliased groups, used by tests. */
	static List<String> aliasesFor(String npcName)
	{
		List<String> aliased = ALIASES.get(normalize(npcName));
		return aliased == null ? Collections.emptyList() : Collections.unmodifiableList(aliased);
	}

	static List<String> allAliasedNpcNames()
	{
		List<String> names = new ArrayList<>(ALIASES.keySet());
		Collections.sort(names);
		return names;
	}

	static boolean sameGroup(String a, String b)
	{
		return normalize(a).equals(normalize(b));
	}
}
