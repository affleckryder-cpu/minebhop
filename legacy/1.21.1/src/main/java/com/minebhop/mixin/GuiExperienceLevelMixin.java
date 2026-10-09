package com.minebhop.mixin;

import com.minebhop.hud.SpeedometerRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla's XP level number while the speed readout is drawn over the XP bar, so the two
 * numbers do not show through each other.
 *
 * <p>26.x does this through each loader's HUD layer API. Fabric for 1.21.1 has no such API -- its
 * only HUD hook draws after everything vanilla -- so here it is a mixin, which works the same on
 * both loaders.
 */
@Mixin(Gui.class)
public class GuiExperienceLevelMixin {

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void minebhop$hideLevelUnderSpeed(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (SpeedometerRenderer.xpBarActive()) {
			ci.cancel();
		}
	}
}
