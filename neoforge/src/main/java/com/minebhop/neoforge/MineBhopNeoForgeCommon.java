package com.minebhop.neoforge;

import com.minebhop.MineBhop;
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
 * <p>The client's handler is registered by {@link MineBhopNeoForge}, which only loads on the client.
 */
@Mod(MineBhop.MOD_ID)
public class MineBhopNeoForgeCommon {

	public MineBhopNeoForgeCommon(IEventBus modEventBus) {
		modEventBus.addListener(MineBhopNeoForgeCommon::onRegisterPayloads);
		NeoForge.EVENT_BUS.addListener(MineBhopNeoForgeCommon::onPlayerLoggedIn);
	}

	private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").optional().playToClient(BhopAllowedPayload.TYPE, BhopAllowedPayload.CODEC);
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(BhopAllowedPayload.TYPE)) {
			player.connection.send(new BhopAllowedPayload());
		}
	}
}
