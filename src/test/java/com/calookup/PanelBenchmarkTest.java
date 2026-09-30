package com.calookup;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Assume;
import org.junit.Test;

/**
 * Opt-in timing harness for the panel beside a heavyweight canvas, as in RuneLite. Skipped
 * unless {@code CA_BENCH} is set:
 * <pre>
 *   CA_BENCH=1 ./gradlew test --tests com.calookup.PanelBenchmarkTest -i | grep BENCH
 * </pre>
 * {@code BENCH_SHOTS} saves PNGs of the panel to that directory; {@code BENCH_SAMPLE} and
 * {@code BENCH_SAMPLE0} name an operation to profile in the warm and cold rounds.
 */
public class PanelBenchmarkTest
{
	private static final String[] WORDS = {"Perfect", "Speed", "Chaser", "Runner", "Master", "Noxious", "Foe", "Chambers",
		"Theatre", "Blood", "Tombs", "Amascut", "Expert", "Mode", "Trio", "Solo", "Kill", "Count", "Veteran", "Adept",
		"Nightmare", "Phosani", "Hydra", "Zulrah", "Vorkath", "Inferno", "Fight", "Caves", "Gauntlet", "Corrupted"};
	private static final String[] BOSSES = new String[70];
	static
	{
		for (int i = 0; i < BOSSES.length; i++)
		{
			BOSSES[i] = "Boss " + (char) ('A' + i % 26) + (i / 26);
		}
		BOSSES[3] = "Theatre of Blood: Hard Mode";
	}

	static List<CombatTask> tasks()
	{
		Random r = new Random(1);
		int[] perTier = {33, 41, 63, 129, 129, 90};
		TaskType[] types = TaskType.values();
		List<CombatTask> out = new ArrayList<>();
		int id = 0;
		for (TaskTier tier : TaskTier.values())
		{
			for (int i = 0; i < perTier[tier.ordinal()]; i++)
			{
				int words = r.nextInt(10) < 6 ? 1 + r.nextInt(2) : 3 + r.nextInt(3);
				StringBuilder name = new StringBuilder();
				for (int w = 0; w < words; w++)
				{
					name.append(w == 0 ? "" : " ").append(WORDS[r.nextInt(WORDS.length)]);
				}
				String boss = BOSSES[r.nextInt(BOSSES.length)];
				String desc = "Kill " + boss + " " + (1 + r.nextInt(200)) + " times.";
				out.add(new CombatTask(id++, name.toString(), desc, tier, types[r.nextInt(types.length)], boss));
			}
		}
		return out;
	}

	private static volatile boolean sampling;
	private static final Map<String, Integer> LEAF = new HashMap<>();
	private static final Map<String, Integer> STACKS = new HashMap<>();

	private static Thread sampler(Thread edt)
	{
		Thread t = new Thread(() ->
		{
			while (sampling)
			{
				StackTraceElement[] st = edt.getStackTrace();
				if (st.length > 0)
				{
					LEAF.merge(st[0].toString(), 1, Integer::sum);
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < Math.min(st.length, 26); i++)
					{
						sb.append(" | ").append(st[i]);
					}
					STACKS.merge(sb.toString(), 1, Integer::sum);
				}
				try
				{
					Thread.sleep(1);
				}
				catch (InterruptedException e)
				{
					return;
				}
			}
		});
		t.setDaemon(true);
		return t;
	}

	private static void dumpSamples()
	{
		System.out.println("BENCH top leaf frames:");
		LEAF.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(10)
			.forEach(e -> System.out.println("BENCH   " + e.getValue() + "  " + e.getKey()));
		System.out.println("BENCH top stacks:");
		STACKS.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
			.forEach(e -> System.out.println("BENCH   " + e.getValue() + " samples:" + e.getKey()));
		LEAF.clear();
		STACKS.clear();
	}

	private static long time(String label, Runnable r) throws Exception
	{
		long t0 = System.nanoTime();
		SwingUtilities.invokeAndWait(r);
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> { });
		long ms = (System.nanoTime() - t0) / 1_000_000;
		System.out.println(String.format("BENCH %-40s %6d ms", label, ms));
		return ms;
	}

	private static long sampled(String label, Runnable r) throws Exception
	{
		Thread[] edt = new Thread[1];
		SwingUtilities.invokeAndWait(() -> edt[0] = Thread.currentThread());
		sampling = true;
		Thread s = sampler(edt[0]);
		s.start();
		long ms = time(label, r);
		sampling = false;
		s.join();
		dumpSamples();
		return ms;
	}

	private static <T extends Component> List<T> find(Container c, Class<T> type, List<T> out)
	{
		for (Component child : c.getComponents())
		{
			if (type.isInstance(child))
			{
				out.add(type.cast(child));
			}
			if (child instanceof Container)
			{
				find((Container) child, type, out);
			}
		}
		return out;
	}

	private static int countComponents(Container c)
	{
		int n = 0;
		for (Component child : c.getComponents())
		{
			n++;
			if (child instanceof Container)
			{
				n += countComponents((Container) child);
			}
		}
		return n;
	}

	/** Paints the whole sidebar (not just the visible viewport) to a PNG in the scratch directory. */
	private static void screenshot(LookupPanel panel, String name) throws Exception
	{
		String dir = System.getenv("BENCH_SHOTS");
		if (dir == null)
		{
			return;
		}
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				int h = Math.min(panel.getHeight(), 1400);
				BufferedImage img = new BufferedImage(panel.getWidth(), h, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = img.createGraphics();
				panel.paint(g);
				g.dispose();
				ImageIO.write(img, "png", new File(dir, name + ".png"));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		System.out.println("BENCH screenshot " + name);
	}

	@Test
	public void benchmark() throws Exception
	{
		Assume.assumeTrue("set CA_BENCH=1 to run the panel benchmark", System.getenv("CA_BENCH") != null);
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		String sampleLabel = System.getenv().getOrDefault("BENCH_SAMPLE", "");
		String sampleLabel0 = System.getenv().getOrDefault("BENCH_SAMPLE0", "");
		List<CombatTask> all = tasks();
		List<CombatTask> zulrah = new ArrayList<>();
		for (CombatTask t : all)
		{
			if (t.getBoss().equals(BOSSES[3]))
			{
				zulrah.add(t);
			}
		}
		Set<Integer> completed = new HashSet<>();
		Random r = new Random(2);
		for (CombatTask t : all)
		{
			if (r.nextInt(10) < 4)
			{
				completed.add(t.getId());
			}
		}
		Map<Integer, TaskProgress> progress = new HashMap<>();
		int n = 0;
		for (CombatTask t : zulrah)
		{
			if (n++ % 3 == 0)
			{
				progress.put(t.getId(), n % 2 == 0 ? TaskProgress.forKillCount(61, 150) : TaskProgress.forSpeed(72, 80));
			}
		}
		CompletionRates rates = CompletionRates.loadSnapshot(new Gson());

		LookupPanel[] panelRef = new LookupPanel[1];
		JFrame[] frameRef = new JFrame[1];
		SwingUtilities.invokeAndWait(() ->
		{
			LookupPanel panel = new LookupPanel(null, Theme.TWILIGHT, TaskArranger.Filter.ALL,
				TaskArranger.SortMode.WIKI_RATE, true);
			panel.updateRates(rates);
			JFrame frame = new JFrame("bench");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setLayout(new BorderLayout());
			Canvas canvas = new Canvas();
			canvas.setBackground(Color.BLACK);
			canvas.setPreferredSize(new Dimension(765, 503));
			frame.add(canvas, BorderLayout.CENTER);
			Component wrapped = panel.getWrappedPanel();
			wrapped.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH + 20, 503));
			frame.add(wrapped, BorderLayout.EAST);
			frame.pack();
			frame.setVisible(true);
			panelRef[0] = panel;
			frameRef[0] = frame;
		});
		LookupPanel panel = panelRef[0];
		Thread.sleep(500);

		LookupView allView = new LookupView(BossOption.ALL, Collections.singletonList(BossOption.ALL), all, completed, true);
		List<String> modes = List.of("Theatre of Blood", "Theatre of Blood: Entry Mode", BOSSES[3]);
		LookupView bossView = new LookupView(BOSSES[3], modes, zulrah, completed, true, progress, "312 points  ·  8 more for Hard");
		LookupView bossOut = new LookupView(BOSSES[3], modes, zulrah, Collections.emptySet(), false);
		LookupView bossView2 = new LookupView(BOSSES[5], Collections.singletonList(BOSSES[5]), zulrah, completed, true);

		time("show boss for screenshot", () -> panel.showLookup(bossView));
		screenshot(panel, "boss-logged-in");
		time("show boss logged out", () -> panel.showLookup(bossOut));
		screenshot(panel, "boss-logged-out");
		time("show (All) for screenshot", () -> panel.showLookup(allView));
		screenshot(panel, "all");

		for (int round = 0; round < 3; round++)
		{
			System.out.println("BENCH --- round " + round);
			run(round, sampleLabel, sampleLabel0, "show boss (warm-up)", () -> panel.showLookup(bossView2));
			run(round, sampleLabel, sampleLabel0, "show (All)", () -> panel.showLookup(allView));
			System.out.println("BENCH components in panel: " + countComponents(panel));
			List<JToggleButton> buttons = find(panel, JToggleButton.class, new ArrayList<>());
			run(round, sampleLabel, sampleLabel0, "(All): filter -> Incomplete", () -> buttons.get(1).doClick(0));
			run(round, sampleLabel, sampleLabel0, "(All): filter -> All", () -> buttons.get(0).doClick(0));
			run(round, sampleLabel, sampleLabel0, "(All): updateCompletion", () -> panel.updateCompletion(completed, true, Collections.emptyMap(), "x"));
			@SuppressWarnings("rawtypes")
			List<JComboBox> boxes = find(panel, JComboBox.class, new ArrayList<>());
			JComboBox<?> sortBox = null;
			for (JComboBox<?> b : boxes)
			{
				if (b.getItemCount() == TaskArranger.SortMode.values().length)
				{
					sortBox = b;
				}
			}
			JComboBox<?> sb = sortBox;
			run(round, sampleLabel, sampleLabel0, "(All): sort -> Name", () -> sb.setSelectedItem(TaskArranger.SortMode.NAME));
			run(round, sampleLabel, sampleLabel0, "(All): sort -> Rate", () -> sb.setSelectedItem(TaskArranger.SortMode.WIKI_RATE));
			run(round, sampleLabel, sampleLabel0, "(All): updateRates (rebuild)", () -> panel.updateRates(rates));
			run(round, sampleLabel, sampleLabel0, "(All) -> boss", () -> panel.showLookup(bossView));
			run(round, sampleLabel, sampleLabel0, "boss -> (All)", () -> panel.showLookup(allView));
			run(round, sampleLabel, sampleLabel0, "(All) -> boss again", () -> panel.showLookup(bossView));
		}
		SwingUtilities.invokeAndWait(frameRef[0]::dispose);
	}

	private static void run(int round, String sampleLabel, String sampleLabel0, String label, Runnable r) throws Exception
	{
		if ((round == 1 && label.equals(sampleLabel)) || (round == 0 && label.equals(sampleLabel0)))
		{
			sampled(label, r);
		}
		else
		{
			time(label, r);
		}
	}
}
