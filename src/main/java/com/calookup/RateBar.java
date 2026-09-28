package com.calookup;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * A thin rounded progress bar. Used both for a task's wiki completion rate and for the
 * player's own progress through a boss or tier.
 */
class RateBar extends JComponent
{
	private double fraction;
	private Color fill;
	private Color track;
	private final int height;

	RateBar(int height, Color fill, Color track)
	{
		this.height = height;
		this.fill = fill;
		this.track = track;
		setOpaque(false);
		setPreferredSize(new Dimension(60, height));
		setMinimumSize(new Dimension(10, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	/** @param fraction 0..1, or negative for "unknown" (draws only the track) */
	void setFraction(double fraction)
	{
		this.fraction = fraction;
		repaint();
	}

	void setColors(Color fill, Color track)
	{
		this.fill = fill;
		this.track = track;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = Math.min(getHeight(), height);
		int y = (getHeight() - h) / 2;
		g2.setColor(track);
		g2.fillRoundRect(0, y, w, h, h, h);
		if (fraction > 0)
		{
			int fw = (int) Math.round(w * Math.min(1.0, fraction));
			fw = Math.max(fw, h);
			g2.setColor(fill);
			g2.fillRoundRect(0, y, fw, h, h, h);
		}
		g2.dispose();
	}
}
