package com.calookup;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * Colour palettes for the lookup panel. Each theme colours the six tiers differently so the
 * sections read at a glance, and picks its own paper, ink and accent.
 */
public enum Theme
{
	/** Aged paper on dark leather, tier ribbons like wax seals. */
	SLAYERS_LEDGER("Slayer's Ledger",
		new Color(0x1f1b17), new Color(0x2a241e), new Color(0x362e25), new Color(0x3d3327),
		new Color(0xf1e6d0), new Color(0xa89a82), new Color(0xd4a24c), new Color(0x4a3f33),
		new Color(0x7fb069), new Color(0x4f9dde), new Color(0xe0a33a),
		new Color(0xa77bde), new Color(0xd9534f), new Color(0xf2c14e)),

	/** RuneLite's own greys with the brand orange. */
	RUNELITE("RuneLite",
		ColorScheme.DARK_GRAY_COLOR, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARKER_GRAY_HOVER_COLOR,
		ColorScheme.DARK_GRAY_HOVER_COLOR, Color.WHITE, ColorScheme.LIGHT_GRAY_COLOR, ColorScheme.BRAND_ORANGE,
		ColorScheme.MEDIUM_GRAY_COLOR,
		new Color(0x6fbf73), new Color(0x5b9bd5), new Color(0xd9a441),
		new Color(0xb58bd9), new Color(0xe06060), new Color(0xf0c419)),

	/** Embers on obsidian, tiers climbing from warm yellow to white heat. */
	INFERNO("Inferno",
		new Color(0x120c0a), new Color(0x1e1210), new Color(0x2a1814), new Color(0x33201a),
		new Color(0xffe9d6), new Color(0xb08a78), new Color(0xff6b35), new Color(0x4a2a22),
		new Color(0xffd166), new Color(0xffa94d), new Color(0xff7b2e),
		new Color(0xff4d4d), new Color(0xd61c4e), new Color(0xfff1f1)),

	/** Cool night blues with pastel tiers. */
	TWILIGHT("Twilight",
		new Color(0x14161f), new Color(0x1c1f2b), new Color(0x262a3a), new Color(0x2c3143),
		new Color(0xe8ecff), new Color(0x8f97b8), new Color(0x7aa2ff), new Color(0x3a4060),
		new Color(0x5ad19a), new Color(0x5aa9ff), new Color(0xffb45a),
		new Color(0xc58aff), new Color(0xff6f91), new Color(0xffd86b));

	private final String label;
	private final Color background;
	private final Color card;
	private final Color cardHover;
	private final Color cardOpen;
	private final Color text;
	private final Color muted;
	private final Color accent;
	private final Color track;
	private final Color[] tierColors;

	Theme(String label, Color background, Color card, Color cardHover, Color cardOpen, Color text, Color muted,
		Color accent, Color track, Color easy, Color medium, Color hard, Color elite, Color master, Color grandmaster)
	{
		this.label = label;
		this.background = background;
		this.card = card;
		this.cardHover = cardHover;
		this.cardOpen = cardOpen;
		this.text = text;
		this.muted = muted;
		this.accent = accent;
		this.track = track;
		this.tierColors = new Color[]{easy, medium, hard, elite, master, grandmaster};
	}

	public Color getBackground()
	{
		return background;
	}

	public Color getCard()
	{
		return card;
	}

	public Color getCardHover()
	{
		return cardHover;
	}

	public Color getCardOpen()
	{
		return cardOpen;
	}

	public Color getText()
	{
		return text;
	}

	public Color getMuted()
	{
		return muted;
	}

	public Color getAccent()
	{
		return accent;
	}

	/** Background of an empty progress bar. */
	public Color getTrack()
	{
		return track;
	}

	public Color tierColor(TaskTier tier)
	{
		return tierColors[tier.ordinal()];
	}

	@Override
	public String toString()
	{
		return label;
	}
}
