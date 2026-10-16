package com.minebhop.mixin;

import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the server a surfing player is on the ground, without telling the client.
 *
 * <p>The server works out fall damage from how far a player drops without touching the ground, and
 * a ride down a surf ramp is a long drop, so it would hurt at the bottom. Riding a surface is near
 * enough to touching one. Setting the flag for real would also make the client play the walking
 * animation and view bob the whole way down, so it is set only around the position packet.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerPositionMixin {

	@Unique
	private boolean minebhop$realOnGround;

	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void minebhop$reportSurfAsGround(CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		minebhop$realOnGround = player.onGround();
		if (SourceMoveHandler.surfing()) {
			player.setOnGround(true);
		}
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void minebhop$restoreGround(CallbackInfo ci) {
		((LocalPlayer) (Object) this).setOnGround(minebhop$realOnGround);
	}
}
