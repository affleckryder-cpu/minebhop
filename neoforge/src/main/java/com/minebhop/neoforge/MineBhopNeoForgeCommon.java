package com.minebhop.neoforge;

import com.minebhop.MineBhop;
import com.minebhop.net.BhopAllowedPayload;
import com.minebhop.config.ConfigManager;
import com.minebhop.net.BhopSettingsPayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.nio.file.Path;
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
		NeoForge.EVENT_BUS.addListener(MineBhopNeoForgeCommon::onServerStarted);
	}

	private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1").optional();
		registrar.playToClient(BhopAllowedPayload.TYPE, BhopAllowedPayload.CODEC);
		registrar.playToClient(BhopSettingsPayload.TYPE, BhopSettingsPayload.CODEC);
	}

	private static void onServerStarted(ServerStartedEvent event) {
		// Writes minebhop-server.json on a dedicated server's first start, so owners can find it.
		if (event.getServer().isDedicatedServer()) {
			ConfigManager.readServerSettings(serverFile());
		}
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (player.connection.hasChannel(BhopAllowedPayload.TYPE)) {
			player.connection.send(new BhopAllowedPayload());
		}
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server != null && server.isDedicatedServer() && player.connection.hasChannel(BhopSettingsPayload.TYPE)) {
			String settings = ConfigManager.readServerSettings(serverFile());
			if (settings != null) {
				player.connection.send(new BhopSettingsPayload(settings));
			}
		}
	}

	private static Path serverFile() {
		return FMLPaths.CONFIGDIR.get().resolve(ConfigManager.SERVER_FILE);
	}
}
