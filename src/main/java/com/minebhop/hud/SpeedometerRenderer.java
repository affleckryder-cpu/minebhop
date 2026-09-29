package com.minebhop.hud;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
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
 * Draws the speedometer and hop timing readout above the hotbar.
 *
 * <p>Loader-agnostic on purpose. Fabric's {@code HudElement.extractRenderState} and NeoForge's
 * {@code GuiLayer.render} happen to take the same two arguments, so both loaders register a
 * one-line adapter that delegates here rather than duplicating the drawing.
 *
 * <p>The timing line is the part that matters for actually learning to hop: it reports how many
 * ticks you spent on the ground before leaving it again, and how much speed the last hop gained or
 * lost. Zero ground ticks means you left on the landing tick and paid no friction at all.
 */
public final class SpeedometerRenderer {
	private SpeedometerRenderer() {
	}

	private static final int COLOUR_TEXT = 0xFFE0E0E0;
	private static final int COLOUR_DIM = 0xFF909090;
	private static final int COLOUR_GAIN = 0xFF55FF55;
	private static final int COLOUR_LOSS = 0xFFFF5555;
	private static final int COLOUR_PERFECT = 0xFF55FFFF;
	private static final int COLOUR_BACKDROP = 0x80000000;

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

		Vec3 velocity = player.getDeltaMovement();
		double blocksPerTick = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		double unitsPerSecond = blocksPerTick * config.unitsPerBlock * 20.0;

		String speedLine = String.format(Locale.ROOT, "%.0f u/s", unitsPerSecond);
		String detailLine = String.format(Locale.ROOT, "%.3f b/t   %s",
				blocksPerTick, player.onGround() ? "GROUND" : "AIR");

		String timingLine;
		int timingColour;
		if (state.hopStreak() > 0) {
			timingLine = String.format(Locale.ROOT, "x%d   %dt   %+.0f u/s",
					state.hopStreak(), state.lastHopGroundTicks(), state.speedDelta());
			timingColour = state.lastHopPerfect() ? COLOUR_PERFECT
					: (state.speedDelta() >= 0.0 ? COLOUR_GAIN : COLOUR_LOSS);
		} else {
			timingLine = String.format(Locale.ROOT, "%s   %dHz", config.bhopMode, config.simulationTickrate);
			timingColour = COLOUR_DIM;
		}

		int width = Math.max(font.width(speedLine), Math.max(font.width(detailLine), font.width(timingLine)));
		int centreX = graphics.guiWidth() / 2;
		int left = centreX - width / 2;
		int top = graphics.guiHeight() - 72;

		graphics.fill(left - 4, top - 3, left + width + 4, top + 32, COLOUR_BACKDROP);

		graphics.text(font, speedLine, centreX - font.width(speedLine) / 2, top, COLOUR_TEXT, true);
		graphics.text(font, detailLine, centreX - font.width(detailLine) / 2, top + 11, COLOUR_DIM, true);
		graphics.text(font, timingLine, centreX - font.width(timingLine) / 2, top + 21, timingColour, true);
	}
}
