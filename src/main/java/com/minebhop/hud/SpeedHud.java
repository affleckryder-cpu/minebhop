package com.minebhop.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Fabric adapter for {@link SpeedometerRenderer}. */
public class SpeedHud implements HudElement {

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		SpeedometerRenderer.draw(graphics, deltaTracker);
	}
}
