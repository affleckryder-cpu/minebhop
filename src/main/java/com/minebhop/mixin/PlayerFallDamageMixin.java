package com.minebhop.mixin;

import com.minebhop.MineBhop;
import com.minebhop.movement.SourceMoveHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional fall damage suppression, on by default.
 *
 * <p>A CS:GO jump is 57 units, about 1.45 blocks, so hopping on the flat is harmless. Chained
 * hops down any kind of slope are not: Minecraft accumulates fall distance across a descent
 * that Source would shrug off, and the mod is unusable in hilly terrain without this.
 *
 * <p>Cancelling only on {@link LocalPlayer} is not enough, because damage is decided by the
 * authoritative side, not the client — {@code LivingEntity#hurtServer} is where it actually
 * lands. In singleplayer and on a LAN world the integrated server runs inside this same JVM,
 * so its {@link ServerPlayer} is transformed by this client-environment mixin too and can be
 * cancelled directly. That is gated on the UUID matching the logged-in profile, so on a LAN
 * world the host stops taking fall damage while everyone else still does.
 *
 * <p>Against a dedicated server there is nothing to do from here: the client's prediction is
 * cancelled, the server applies its own damage regardless. What survives in that case is the
 * momentum, via {@link EntityLerpMotionMixin}.
 */
@Mixin(Player.class)
public class PlayerFallDamageMixin {

	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void minebhop$noFallDamage(double fallDistance, float damageModifier, DamageSource damageSource,
			CallbackInfoReturnable<Boolean> cir) {
		if (!MineBhop.config().enabled || !MineBhop.config().disableFallDamage || !SourceMoveHandler.allowedHere()) {
			return;
		}

		Player self = (Player) (Object) this;

		if (self instanceof LocalPlayer) {
			cir.setReturnValue(false);
			return;
		}

		if (self instanceof ServerPlayer) {
			Minecraft client = Minecraft.getInstance();
			if (client.hasSingleplayerServer() && self.getUUID().equals(client.getUser().getProfileId())) {
				cir.setReturnValue(false);
			}
		}
	}
}
