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
 * Stops server velocity corrections from wiping out a hop chain.
 *
 * <p>Taking damage makes the server call {@code markHurt()}, which sets {@code hurtMarked} and
 * makes {@code ServerEntity} broadcast a {@code ClientboundSetEntityMotionPacket} -- and for a
 * player that packet is sent to the damaged player as well as to everyone tracking them. The
 * client handles it here, in {@code Entity#lerpMotion}, by overwriting delta movement outright.
 *
 * <p>The server never simulates Source movement. Its stored velocity for the player is derived
 * from position packets and bears no relation to the several-hundred-u/s figure the client is
 * actually carrying, so applying it destroys the momentum. Fall damage is the case you hit
 * constantly, because it is in the {@code no_knockback} tag but <i>not</i> in {@code no_impact}:
 * no knockback is applied, yet {@code markHurt()} still fires and the sync still lands.
 *
 * <p>Rather than ignore the packet wholesale, this only rejects corrections that would
 * <i>reduce</i> horizontal speed. Anything that would speed the player up is real knockback --
 * explosions, wind charges, mobs -- and is passed through untouched. The vertical component is
 * always accepted, so bounces, elytra syncs and being launched still behave.
 */
@Mixin(Entity.class)
public class EntityLerpMotionMixin {

	@Inject(method = "lerpMotion", at = @At("HEAD"), cancellable = true)
	private void minebhop$preserveMomentum(Vec3 movement, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
		if (!MineBhop.config().preserveMomentumOnDamage || !SourceMoveHandler.shouldHandle(player)) {
			return;
		}

		Vec3 current = player.getDeltaMovement();
		double currentHorizontal = current.x * current.x + current.z * current.z;
		double incomingHorizontal = movement.x * movement.x + movement.z * movement.z;

		// Faster than we are going: genuine knockback, let it through.
		if (incomingHorizontal >= currentHorizontal) {
			return;
		}

		player.setDeltaMovement(current.x, movement.y, current.z);
		ci.cancel();
	}
}
