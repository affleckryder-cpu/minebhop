package com.minebhop.neoforge;

import com.minebhop.hud.SpeedometerRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * NeoForge adapter for {@link SpeedometerRenderer}.
 *
 * <p>{@code GuiLayer.render} happens to take exactly the same arguments as Fabric's
 * {@code HudElement.extractRenderState}, so both are one-line delegates onto shared drawing code.
 */
public class NeoForgeSpeedHud implements GuiLayer {

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		SpeedometerRenderer.draw(graphics, deltaTracker);
	}
}
