package com.minebhop.neoforge;

import com.minebhop.MineBhop;
import com.minebhop.movement.SourceMoveHandler;
import com.minebhop.net.BhopAllowedPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Runs on both sides. On a server -- dedicated, or the integrated one behind a LAN world -- it
 * tells every joining player who has the mod that Source movement is allowed. The payload is
 * optional, so players without the mod, vanilla ones included, join as normal.
 *
 * <p>NeoForge for 1.21.1 registers a payload and its handler together, where 26.x has a separate
 * client-side event. The handler below is only ever invoked on a client; a dedicated server
 * registers it but never runs it, so the client-only class it names is never loaded there.
 */
@Mod(MineBhop.MOD_ID)
public class MineBhopNeoForgeCommon {

	public MineBhopNeoForgeCommon(IEventBus modEventBus) {
		modEventBus.addListener(MineBhopNeoForgeCommon::onRegisterPayloads);
		NeoForge.EVENT_BUS.addListener(MineBhopNeoForgeCommon::onPlayerLoggedIn);
	}

	private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").optional().playToClient(BhopAllowedPayload.TYPE, BhopAllowedPayload.CODEC,
				(payload, context) -> SourceMoveHandler.markServerAllowed());
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(BhopAllowedPayload.TYPE)) {
			player.connection.send(new BhopAllowedPayload());
		}
	}
}
