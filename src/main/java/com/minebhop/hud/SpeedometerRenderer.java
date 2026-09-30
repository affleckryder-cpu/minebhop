package com.minebhop.hud;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
import com.minebhop.movement.JumpStats;
import com.minebhop.movement.MovementState;
import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Draws the HUD panel above the hotbar -- a status line and, with jumpStats on, the last jump as a
 * small grid -- and the speed readout on the XP bar ({@link #drawXpBar}).
 *
 * <p>Loader-agnostic on purpose. Fabric's {@code HudElement.extractRenderState} and NeoForge's
 * {@code GuiLayer.render} happen to take the same two arguments, so both loaders register a
 * one-line adapter that delegates here rather than duplicating the drawing.
 *
 * <p>The status line is the part that matters for learning to hop: while a chain is going it shows
 * the streak, how many ticks you spent on the ground before leaving again, and the speed the last
 * hop gained or lost. Zero ground ticks means you left on the landing tick and paid no friction.
 */
public final class SpeedometerRenderer {
	private SpeedometerRenderer() {
	}

	private static final int COLOUR_TEXT = 0xFFFFFFFF;
	private static final int COLOUR_DIM = 0xFF8A8F98;
	private static final int COLOUR_GAIN = 0xFF5CFF8A;
	private static final int COLOUR_LOSS = 0xFFFF5C6C;
	private static final int COLOUR_ACCENT = 0xFF5CE1FF;
	private static final int COLOUR_PANEL = 0x90101218;
	private static final int COLOUR_RULE = 0x20FFFFFF;

	/** Speed that fills the XP bar. Past it the bar stays full. */
	private static final double BAR_FULL_SPEED = 1000.0;
	private static final int MIN_WIDTH = 120;
	private static final int PAD = 6;
	/** Gap between the panel and the bottom of the screen, clearing the hotbar and XP bar. */
	private static final int BOTTOM_MARGIN = 42;

	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		BhopConfig config = MineBhop.config();

		if (!config.hud || !config.enabled || !SourceMoveHandler.allowedHere()) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		MovementState state = MineBhop.state();
		Font font = client.font;

		// Accent follows the last hop: green for a gain, red for a loss, cyan otherwise.
		boolean chain = state.hopStreak() > 0;
		int accent = !chain ? COLOUR_ACCENT : state.speedDelta() >= 0.0 ? COLOUR_GAIN : COLOUR_LOSS;

		String status = chain
				? String.format(Locale.ROOT, "x%d  ·  %dt  ·  %+.0f", state.hopStreak(), state.lastHopGroundTicks(), state.speedDelta())
				: String.format(Locale.ROOT, "%s  ·  %s  ·  %dHz",
						player.onGround() ? "GROUND" : "AIR", config.bhopMode, config.simulationTickrate);

		JumpStats.Result jump = SourceMoveHandler.lastJump();
		String[][] cells = jump == null ? null : new String[][] {
				{ "DIST", String.format(Locale.ROOT, "%.2f", jump.distance()) },
				{ "PRE", String.format(Locale.ROOT, "%.0f", jump.preSpeed()) },
				{ "MAX", String.format(Locale.ROOT, "%.0f", jump.maxSpeed()) },
				{ "SYNC", jump.sync() + "%" },
				{ "STRAFES", Integer.toString(jump.strafes()) },
				{ "HEIGHT", Math.abs(jump.height()) > 0.01 ? String.format(Locale.ROOT, "%+.2f", jump.height()) : "-" },
		};

		// --- measure ---
		int width = Math.max(MIN_WIDTH, font.width(status) + PAD * 2);
		int cellWidth = 0;
		if (cells != null) {
			for (String[] cell : cells) {
				cellWidth = Math.max(cellWidth, font.width(cell[0]) + 3 + font.width(cell[1]));
			}
			width = Math.max(width, (cellWidth + 8) * 3 + PAD * 2);
		}
		int height = PAD + 9 + PAD;
		if (cells != null) {
			height += 4 + 3 + 10 + 9;
		}

		int left = graphics.guiWidth() / 2 - width / 2;
		int right = left + width;
		int top = graphics.guiHeight() - BOTTOM_MARGIN - height;
		int centreX = left + width / 2;
		int innerLeft = left + PAD;
		int innerRight = right - PAD;

		// --- panel ---
		graphics.fill(left, top, right, top + height, COLOUR_PANEL);
		graphics.fill(left, top, right, top + 1, accent);

		// --- status ---
		int y = top + PAD;
		graphics.text(font, status, centreX - font.width(status) / 2, y, chain ? accent : COLOUR_DIM, true);
		y += 9;

		// --- last jump grid ---
		if (cells != null) {
			y += 4;
			graphics.fill(innerLeft, y, innerRight, y + 1, COLOUR_RULE);
			y += 3;
			int columnWidth = (width - PAD * 2) / 3;
			for (int i = 0; i < cells.length; i++) {
				int column = i % 3;
				int rowY = y + (i / 3) * 10;
				String label = cells[i][0];
				String value = cells[i][1];
				int cellX = innerLeft + column * columnWidth
						+ (columnWidth - (font.width(label) + 3 + font.width(value))) / 2;
				graphics.text(font, label, cellX, rowY, COLOUR_DIM, true);
				graphics.text(font, value, cellX + font.width(label) + 3, rowY, COLOUR_TEXT, true);
			}
		}
	}

	private static final int XP_GREEN = 0xFF55FF55;
	private static final int XP_YELLOW = 0xFFFFE155;
	private static final int XP_RED = 0xFFFF4040;
	/** Below this the vanilla XP bar shows through, so standing still you still see your level. */
	private static final double XP_BAR_MIN_SPEED = 10.0;

	/** Horizontal speed in u/s, or -1 when the XP bar speedometer should not show. */
	private static double xpBarSpeed() {
		Minecraft client = Minecraft.getInstance();
		BhopConfig config = MineBhop.config();
		LocalPlayer player = client.player;
		if (!config.xpBarSpeed || !config.enabled || player == null || !SourceMoveHandler.allowedHere()
				|| client.gameMode == null || !client.gameMode.hasExperience()) {
			return -1.0;
		}
		Vec3 velocity = player.getDeltaMovement();
		double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * config.unitsPerBlock * 20.0;
		return speed < XP_BAR_MIN_SPEED ? -1.0 : speed;
	}

	/** Whether the speed bar is covering the XP bar right now, so vanilla's level number should hide. */
	public static boolean xpBarActive() {
		return xpBarSpeed() >= 0.0;
	}

	/**
	 * Speed drawn over the vanilla XP bar: the bar fills with speed and runs green to yellow to red
	 * along its length, and the level number shows u/s.
	 *
	 * <p>Must be layered after the contextual info bar, not the experience level: in 26.x vanilla
	 * draws the level number first and the bar fill after it, and skips the number entirely at
	 * level 0 -- which would skip anything attached to it too.
	 */
	public static void drawXpBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		double speed = xpBarSpeed();
		if (speed < 0.0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();

		// Vanilla's XP bar geometry: 182x5, 29 px above the bottom, level number 6 px above that.
		int left = graphics.guiWidth() / 2 - 91;
		int top = graphics.guiHeight() - 29;
		float fraction = (float) Math.min(1.0, speed / BAR_FULL_SPEED);
		int filled = Math.round(182 * fraction);

		graphics.fill(left, top, left + 182, top + 5, 0xFF1A1A1A);
		for (int i = 0; i < filled; i++) {
			int colour = xpColour(i / 181.0f);
			graphics.fill(left + i, top, left + i + 1, top + 4, colour);
			graphics.fill(left + i, top + 4, left + i + 1, top + 5, darken(colour));
		}

		// The level number, outlined the way vanilla draws it.
		Font font = client.font;
		String text = String.format(Locale.ROOT, "%.0f", speed);
		int x = graphics.guiWidth() / 2 - font.width(text) / 2;
		int y = graphics.guiHeight() - 35;
		graphics.text(font, text, x + 1, y, 0xFF000000, false);
		graphics.text(font, text, x - 1, y, 0xFF000000, false);
		graphics.text(font, text, x, y + 1, 0xFF000000, false);
		graphics.text(font, text, x, y - 1, 0xFF000000, false);
		graphics.text(font, text, x, y, xpColour(fraction), false);
	}

	private static int xpColour(float t) {
		return t < 0.5f ? lerpColour(XP_GREEN, XP_YELLOW, t * 2.0f) : lerpColour(XP_YELLOW, XP_RED, (t - 0.5f) * 2.0f);
	}

	private static int darken(int colour) {
		return 0xFF000000 | ((colour >> 1) & 0x7F7F7F);
	}

	private static int lerpColour(int from, int to, float t) {
		int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
