package com.calookup;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Combat Achievement Lookup: right-click a boss or monster, choose "CA Lookup", and see its
 * Combat Achievements grouped by tier with the wiki's completion rate for each task, your own
 * progress read from the game, and a short wiki guide for any task you click.
 * <p>
 * The plugin only ever reads: the task list from the game cache, your completion bits from
 * your varps, and two public read-only wiki endpoints. It adds one right-click option and
 * never changes or removes existing menu entries, sends input, or automates anything.
 */
@Slf4j
@PluginDescriptor(
	name = "CA Lookup",
	description = "Right-click a boss for CA Lookup: its Combat Achievements by tier, wiki completion rates, your progress and quick wiki guides",
	tags = {"combat", "achievements", "achievement", "ca", "cas", "tasks", "boss", "bosses", "pvm", "wiki", "lookup", "guide", "tier", "progress"}
)
public class CombatAchievementLookupPlugin extends Plugin
{
	/** Game message sent when a task is completed; used as a second trigger to refresh. */
	private static final String COMPLETION_MESSAGE = "Congratulations, you've completed";

	/** Live wiki completion rates older than this are fetched again on the next login. */
	private static final Duration RATES_MAX_AGE = Duration.ofHours(12);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private CombatAchievementLookupConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WikiClient wikiClient;

	@Inject
	private Gson gson;

	private CombatTaskRepository repository;
	private LookupPanel panel;
	private NavigationButton navButton;

	private volatile CompletionRates rates = CompletionRates.EMPTY;
	private volatile boolean ratesRequested;

	/** Set by events on the client thread; consumed on the next game tick so bursts coalesce. */
	private boolean completionDirty;

	/** The boss currently shown and the alternatives offered with it, for chip switching. */
	private volatile List<String> currentAlternatives = Collections.emptyList();

	private final Map<Integer, WikiSummary> summaryCache = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> portraitCache = new ConcurrentHashMap<>();
	private final Set<String> portraitsRequested = ConcurrentHashMap.newKeySet();
	private ProgressReader progressReader;

	@Inject
	private ConfigManager configManager;

	@Override
	protected void startUp()
	{
		repository = new CombatTaskRepository(client);
		progressReader = new ProgressReader(configManager);
		rates = CompletionRates.loadSnapshot(gson);

		panel = new LookupPanel(this, config.theme(), config.defaultFilter(), config.defaultSort(),
			config.collapseCompletedTiers());
		panel.updateRates(rates);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("CA Lookup")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			ensureLoaded();
			refreshCompletion();
		});

		if (config.useWiki())
		{
			fetchRates();
		}
		log.debug("Combat Achievement Lookup started");
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = null;
		panel = null;
		if (repository != null)
		{
			repository.clear();
		}
		summaryCache.clear();
		portraitCache.clear();
		portraitsRequested.clear();
		currentAlternatives = Collections.emptyList();
		ratesRequested = false;
		log.debug("Combat Achievement Lookup stopped");
	}

	@Provides
	CombatAchievementLookupConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CombatAchievementLookupConfig.class);
	}

	// ---- Events (client thread) ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// Completion is per character and is re-read from the client on every login, so
			// switching accounts just works. The wiki's global rates are refreshed if stale.
			clientThread.invokeLater(() ->
			{
				ensureLoaded();
				refreshCompletion();
			});
			if (config.useWiki() && ratesStale())
			{
				ratesRequested = false;
				fetchRates();
			}
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// No game ticks arrive while logged out, so refresh straight away.
			completionDirty = false;
			refreshCompletion();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (CombatTaskRepository.isCompletionVarp(event.getVarpId()))
		{
			completionDirty = true;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains(COMPLETION_MESSAGE))
		{
			completionDirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (completionDirty)
		{
			completionDirty = false;
			refreshCompletion();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CombatAchievementLookupConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		final Theme theme = config.theme();
		final TaskArranger.Filter filter = config.defaultFilter();
		final TaskArranger.SortMode sort = config.defaultSort();
		final boolean collapse = config.collapseCompletedTiers();
		final boolean useWiki = config.useWiki();
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			switch (event.getKey())
			{
				case CombatAchievementLookupConfig.KEY_THEME:
					panel.applyTheme(theme);
					break;
				case "defaultFilter":
				case "defaultSort":
					panel.setDefaults(filter, sort);
					break;
				case "collapseCompletedTiers":
					panel.setAutoCollapseFinished(collapse);
					break;
				default:
					break;
			}
		});
		if (CombatAchievementLookupConfig.KEY_USE_WIKI.equals(event.getKey()))
		{
			if (useWiki)
			{
				fetchRates();
			}
			else
			{
				rates = CompletionRates.loadSnapshot(gson);
				ratesRequested = false;
				final CompletionRates snapshot = rates;
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.updateRates(snapshot);
					}
				});
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.addMenuOption() || event.getType() != MenuAction.EXAMINE_NPC.getId())
		{
			return;
		}
		if (config.shiftOnly() && !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}
		final String npcName = npc.getName();
		if (npcName == null || !hasTasks(npcName))
		{
			return;
		}

		// Inserting at the end while the Examine entry is being added places the option just
		// above Examine, well away from the left-click action.
		String option = config.menuOption().trim();
		if (option.isEmpty())
		{
			option = "CA Lookup";
		}
		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.onClick(e -> lookupNpc(npcName));
	}

	// ---- Lookups ----

	/** @return whether any Combat Achievement group matches this NPC name */
	boolean hasTasks(String npcName)
	{
		List<String> groups = BossMatcher.groupsFor(npcName);
		if (!repository.isLoaded())
		{
			// Cache not read yet: trust the alias table so the option is not missing early on.
			return !BossMatcher.aliasesFor(npcName).isEmpty();
		}
		for (String group : groups)
		{
			if (!repository.tasksFor(group).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	/** Opens the lookup for the boss group(s) an NPC belongs to. Safe to call from any thread. */
	void lookupNpc(String npcName)
	{
		clientThread.invoke(() ->
		{
			ensureLoaded();
			List<String> matches = new ArrayList<>();
			for (String group : BossMatcher.groupsFor(npcName))
			{
				String display = repository.resolveBoss(group);
				if (display != null && !matches.contains(display))
				{
					matches.add(display);
				}
			}
			if (matches.isEmpty())
			{
				showMessage("No Combat Achievements found for " + npcName + ".");
				return;
			}
			currentAlternatives = Collections.unmodifiableList(matches);
			showBoss(matches.get(0), matches);
		});
	}

	/** Opens the lookup for a boss group chosen in the panel. Safe to call from any thread. */
	void lookupBoss(String boss)
	{
		clientThread.invoke(() ->
		{
			ensureLoaded();
			if (BossOption.isAll(boss))
			{
				currentAlternatives = Collections.singletonList(BossOption.ALL);
				showBoss(BossOption.ALL, currentAlternatives);
				return;
			}
			String display = repository.resolveBoss(boss);
			if (display == null)
			{
				showMessage("No Combat Achievements found for " + boss + ".");
				return;
			}
			List<String> alternatives = currentAlternatives;
			boolean amongAlternatives = false;
			for (String alt : alternatives)
			{
				if (alt.equals(display))
				{
					amongAlternatives = true;
					break;
				}
			}
			if (!amongAlternatives)
			{
				alternatives = Collections.singletonList(display);
				currentAlternatives = alternatives;
			}
			showBoss(display, alternatives);
		});
	}

	/** Must run on the client thread. */
	private void showBoss(String boss, List<String> alternatives)
	{
		List<CombatTask> tasks = BossOption.isAll(boss) ? repository.getTasks() : repository.tasksFor(boss);
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		Set<Integer> completed = loggedIn ? repository.readCompleted() : Collections.emptySet();
		Map<Integer, TaskProgress> progress = loggedIn ? progressReader.read(tasks) : Collections.emptyMap();
		String pointsLine = loggedIn ? repository.pointsLine(completed) : null;
		final LookupView view = new LookupView(boss, alternatives, tasks, completed, loggedIn, progress, pointsLine);
		final boolean open = config.openPanel();
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			panel.showLookup(view);
			if (open && navButton != null)
			{
				clientToolbar.openPanel(navButton);
			}
		});
		if (config.useWiki() && !BossOption.isAll(boss))
		{
			fetchPortrait(boss);
		}
	}

	/** Shows the boss's wiki image in the header, from cache when possible. */
	private void fetchPortrait(String boss)
	{
		BufferedImage cached = portraitCache.get(boss);
		if (cached != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setPortrait(boss, cached);
				}
			});
			return;
		}
		if (!portraitsRequested.add(boss))
		{
			return;
		}
		wikiClient.fetchPortrait(boss, LookupPanel.PORTRAIT_SIZE,
			image ->
			{
				portraitCache.put(boss, image);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setPortrait(boss, image);
					}
				});
			},
			error ->
			{
				log.debug("No portrait for {}: {}", boss, error);
				portraitsRequested.remove(boss);
			});
	}

	private void showMessage(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showMessage(message);
			}
		});
	}

	/** Reads the task list from the cache if that has not happened yet. Client thread only. */
	private void ensureLoaded()
	{
		if (repository.isLoaded())
		{
			return;
		}
		try
		{
			if (repository.load())
			{
				refreshCompletion();
			}
		}
		catch (RuntimeException e)
		{
			// The cache may not be readable yet; a later login or lookup retries.
			log.debug("Combat achievement task list not available yet", e);
		}
	}

	/** Re-reads completion bits and pushes them to the panel. Client thread only. */
	private void refreshCompletion()
	{
		if (!repository.isLoaded())
		{
			return;
		}
		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		final Set<Integer> completed = loggedIn ? repository.readCompleted() : Collections.emptySet();
		final List<BossOption> options = bossOptions(completed, loggedIn);
		final Map<Integer, TaskProgress> progress = loggedIn ? progressReader.read(repository.getTasks()) : Collections.emptyMap();
		final String pointsLine = loggedIn ? repository.pointsLine(completed) : null;
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setBossOptions(options);
				panel.updateCompletion(completed, loggedIn, progress, pointsLine);
			}
		});
	}

	/** Drop-down entries ordered by points still available. Client thread only. */
	private List<BossOption> bossOptions(Set<Integer> completed, boolean loggedIn)
	{
		List<String> bosses = repository.getBossNames();
		List<List<CombatTask>> tasksOf = new ArrayList<>(bosses.size());
		for (String boss : bosses)
		{
			tasksOf.add(repository.tasksFor(boss));
		}
		return BossOption.build(bosses, tasksOf, repository.getTasks(), completed, loggedIn);
	}

	// ---- Wiki ----

	/** @return true when the live wiki rates were never fetched or are older than {@link #RATES_MAX_AGE} */
	private boolean ratesStale()
	{
		CompletionRates current = rates;
		return current.getSource() != CompletionRates.Source.WIKI
			|| current.getFetchedAt() == null
			|| Duration.between(current.getFetchedAt(), Instant.now()).compareTo(RATES_MAX_AGE) > 0;
	}

	private void fetchRates()
	{
		if (ratesRequested)
		{
			return;
		}
		ratesRequested = true;
		wikiClient.fetchCompletionRates(
			(map, dataDate) ->
			{
				rates = CompletionRates.fromWiki(map, dataDate);
				final CompletionRates live = rates;
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.updateRates(live);
					}
				});
			},
			error ->
			{
				ratesRequested = false;
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setRatesNotice(error);
					}
				});
			});
	}

	/** Called by the panel when a task is expanded. Answers through the panel. */
	void requestSummary(CombatTask task)
	{
		final int id = task.getId();
		WikiSummary cached = summaryCache.get(id);
		if (cached != null)
		{
			panel.showSummary(id, cached);
			return;
		}
		if (!config.useWiki())
		{
			panel.showSummaryError(id, "Wiki fetching is turned off in the plugin settings. Use 'Open on wiki' instead.");
			return;
		}
		wikiClient.fetchTaskSummary(task.getName(),
			summary ->
			{
				summaryCache.put(id, summary);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showSummary(id, summary);
					}
				});
			},
			error -> SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showSummaryError(id, error);
				}
			}));
	}

	void openWiki(CombatTask task)
	{
		LinkBrowser.browse(WikiClient.taskPageUrl(task.getName()).toString());
	}
}
