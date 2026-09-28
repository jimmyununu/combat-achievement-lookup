package com.calookup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * A small circle: hollow while a task is still to do, filled with a tick once it is done, and
 * a dotted ring when completion is unknown because the player is logged out.
 */
class StatusMark extends JComponent
{
	private static final int SIZE = 14;

	private boolean done;
	private boolean unknown;
	private Color color;
	private Color ring;

	StatusMark(Color color, Color ring)
	{
		this.color = color;
		this.ring = ring;
		setOpaque(false);
		Dimension d = new Dimension(SIZE, SIZE);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
	}

	void setState(boolean done, boolean unknown)
	{
		this.done = done;
		this.unknown = unknown;
		repaint();
	}

	void setColors(Color color, Color ring)
	{
		this.color = color;
		this.ring = ring;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int d = SIZE - 2;
		int x = (getWidth() - d) / 2;
		int y = (getHeight() - d) / 2;
		if (done)
		{
			g2.setColor(color);
			g2.fillOval(x, y, d, d);
			g2.setColor(Color.WHITE);
			g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawLine(x + 3, y + d / 2, x + d / 2 - 1, y + d - 4);
			g2.drawLine(x + d / 2 - 1, y + d - 4, x + d - 3, y + 3);
		}
		else if (unknown)
		{
			g2.setColor(ring);
			g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2f, 2f}, 0f));
			g2.drawOval(x, y, d, d);
		}
		else
		{
			g2.setColor(ring);
			g2.setStroke(new BasicStroke(1.4f));
			g2.drawOval(x, y, d, d);
		}
		g2.dispose();
	}
}
