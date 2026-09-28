package com.calookup;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * The header block behind the crest, title and summary. When a boss portrait is available it
 * is painted at the right edge, fading into the background towards the text so the header
 * reads like a bestiary page without hurting legibility.
 */
class PortraitHeader extends JPanel
{
	/** Portrait height in pixels; the width follows the image's aspect ratio. */
	static final int PORTRAIT_HEIGHT = 64;
	private static final float PORTRAIT_ALPHA = 0.9f;

	private BufferedImage portrait;
	private Color background = Color.BLACK;

	PortraitHeader()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
	}

	void setPortrait(BufferedImage portrait)
	{
		this.portrait = portrait;
		repaint();
	}

	void setBackgroundColor(Color background)
	{
		this.background = background;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (portrait == null)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		int h = Math.min(PORTRAIT_HEIGHT, getHeight());
		int w = Math.max(1, (int) Math.round(portrait.getWidth() * (h / (double) portrait.getHeight())));
		int x = getWidth() - w;
		int y = 0;

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, PORTRAIT_ALPHA));
		g2.drawImage(portrait, x, y, w, h, null);

		// Fade the left half of the portrait into the background so text stays readable.
		g2.setComposite(AlphaComposite.SrcOver);
		Color solid = background;
		Color clear = new Color(background.getRed(), background.getGreen(), background.getBlue(), 0);
		g2.setPaint(new GradientPaint(x, 0, solid, x + w * 0.6f, 0, clear));
		g2.fillRect(x, y, w, h);
		g2.dispose();
	}
}
