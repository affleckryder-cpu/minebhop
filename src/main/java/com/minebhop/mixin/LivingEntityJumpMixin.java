package com.minebhop.mixin;

import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla jump for the local player while Source movement is active.
 *
 * <p>Two reasons this has to go. Vanilla applies its own jump impulse plus a sprint boost,
 * which would stack on top of the Source impulse; and {@code aiStep} gates it behind a ten
 * tick {@code noJumpDelay}, which alone would make bunny hopping impossible. Jumping is
 * handled instead inside {@code travel}, where it can run ahead of friction the way Source
 * orders it.
 */
@Mixin(LivingEntity.class)
public class LivingEntityJumpMixin {

	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void minebhop$cancelVanillaJump(CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && SourceMoveHandler.shouldHandle(player)) {
			ci.cancel();
		}
	}
}
