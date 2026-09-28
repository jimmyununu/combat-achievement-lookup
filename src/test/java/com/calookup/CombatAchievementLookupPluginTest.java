package com.calookup;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CombatAchievementLookupPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CombatAchievementLookupPlugin.class);
		RuneLite.main(args);
	}
}
