package com.minebhop.mixin;

import com.minebhop.MineBhop;
import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a server velocity correction from wiping out a hop chain. Same behaviour as the 26.x
 * mixin of this name; see that one for the full reasoning. It has its own copy here only because
 * 1.21.1's {@code lerpMotion} takes three doubles where 26.x takes a {@code Vec3}.
 */
@Mixin(Entity.class)
public class EntityLerpMotionMixin {

	@Inject(method = "lerpMotion", at = @At("HEAD"), cancellable = true)
	private void minebhop$preserveMomentum(double x, double y, double z, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
		if (!MineBhop.config().preserveMomentumOnDamage || !SourceMoveHandler.shouldHandle(player)) {
			return;
		}

		Vec3 current = player.getDeltaMovement();
		double currentHorizontal = current.x * current.x + current.z * current.z;
		double incomingHorizontal = x * x + z * z;

		// Faster than we are going: genuine knockback, let it through.
		if (incomingHorizontal >= currentHorizontal) {
			return;
		}

		player.setDeltaMovement(current.x, y, current.z);
		ci.cancel();
	}
}
