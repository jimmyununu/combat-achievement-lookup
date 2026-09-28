package com.calookup;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CombatAchievementLookupConfig.GROUP)
public interface CombatAchievementLookupConfig extends Config
{
	String GROUP = "calookup";
	String KEY_THEME = "theme";
	String KEY_USE_WIKI = "useWiki";
	String KEY_MENU_OPTION = "menuOption";

	@ConfigSection(
		name = "Right-click menu",
		description = "The CA Lookup option on monsters",
		position = 0
	)
	String menuSection = "menu";

	@ConfigSection(
		name = "Panel",
		description = "How the lookup panel looks and behaves",
		position = 1
	)
	String panelSection = "panel";

	@ConfigSection(
		name = "Wiki",
		description = "What is fetched from the Old School RuneScape Wiki",
		position = 2
	)
	String wikiSection = "wiki";

	@ConfigItem(
		keyName = "addMenuOption",
		name = "Add CA Lookup to monsters",
		description = "Add a 'CA Lookup' option to the right-click menu of any monster that has Combat Achievements",
		section = menuSection,
		position = 0
	)
	default boolean addMenuOption()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shiftOnly",
		name = "Only with Shift held",
		description = "Only add the option while Shift is held, to keep monster menus short",
		section = menuSection,
		position = 1
	)
	default boolean shiftOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_MENU_OPTION,
		name = "Menu option text",
		description = "The text of the right-click option",
		section = menuSection,
		position = 2
	)
	default String menuOption()
	{
		return "CA Lookup";
	}

	@ConfigItem(
		keyName = "openPanel",
		name = "Open the side panel",
		description = "Bring the lookup panel to the front when you use CA Lookup",
		section = panelSection,
		position = 0
	)
	default boolean openPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_THEME,
		name = "Theme",
		description = "Colour palette of the lookup panel",
		section = panelSection,
		position = 1
	)
	default Theme theme()
	{
		return Theme.TWILIGHT;
	}

	@ConfigItem(
		keyName = "defaultFilter",
		name = "Default filter",
		description = "Which tasks to show when a lookup opens. Tier sections are always kept",
		section = panelSection,
		position = 2
	)
	default TaskArranger.Filter defaultFilter()
	{
		return TaskArranger.Filter.ALL;
	}

	@ConfigItem(
		keyName = "defaultSort",
		name = "Default sort",
		description = "Order of tasks inside each tier when a lookup opens",
		section = panelSection,
		position = 3
	)
	default TaskArranger.SortMode defaultSort()
	{
		return TaskArranger.SortMode.WIKI_RATE;
	}

	@ConfigItem(
		keyName = "collapseCompletedTiers",
		name = "Collapse finished tiers",
		description = "Start a tier collapsed when you have completed every task in it",
		section = panelSection,
		position = 4
	)
	default boolean collapseCompletedTiers()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_USE_WIKI,
		name = "Fetch from the wiki",
		description = "Fetch live completion rates and task guides from oldschool.runescape.wiki. "
			+ "When off, the plugin makes no network requests and uses its bundled completion snapshot",
		section = wikiSection,
		position = 0
	)
	default boolean useWiki()
	{
		return true;
	}
}
