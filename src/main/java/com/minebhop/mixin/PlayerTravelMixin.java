package com.minebhop.mixin;

import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the velocity integration for the local player with the Source model.
 *
 * <p>Targets {@code Player} rather than {@code LocalPlayer} because {@code LocalPlayer}
 * does not declare {@code travel} -- {@code Player} is the last class in the hierarchy that
 * overrides it, so this is the lowest override that actually runs.
 */
@Mixin(Player.class)
public class PlayerTravelMixin {

	@Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
	private void minebhop$sourceTravel(Vec3 input, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
		if (!SourceMoveHandler.shouldHandle(player)) {
			return;
		}
		SourceMoveHandler.travel(player);
		ci.cancel();
	}
}
