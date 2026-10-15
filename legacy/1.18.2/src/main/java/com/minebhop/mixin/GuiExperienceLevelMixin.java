package com.minebhop.mixin;

import com.minebhop.hud.SpeedometerRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides vanilla's XP level number while the speed readout is drawn over the XP bar, so the two
 * numbers do not show through each other.
 *
 * <p>In 1.18.2 the bar and the number are drawn by one method, which draws the number only when
 * the level is above zero. So this answers "zero" to that one question while the readout shows.
 */
@Mixin(Gui.class)
public class GuiExperienceLevelMixin {

	@Redirect(method = "renderExperienceBar", at = @At(value = "FIELD", ordinal = 0, opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I"))
	private int minebhop$hideLevelUnderSpeed(LocalPlayer player) {
		return SpeedometerRenderer.xpBarActive() ? 0 : player.experienceLevel;
	}
}
