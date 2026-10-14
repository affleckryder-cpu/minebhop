package com.minebhop.fabric;

import com.minebhop.config.ConfigManager;
import com.minebhop.net.BhopAllowedPayload;
import com.minebhop.net.BhopSettingsPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runs on both sides. On a server -- dedicated, or the integrated one behind a LAN world -- it
 * tells every joining player who has the mod that Source movement is allowed, and the
 * server's physics when it syncs them (see minebhop-server.json). Players without
 * the mod never get the payload, so vanilla clients join as normal.
 */
public class MineBhopFabricCommon implements ModInitializer {

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(BhopAllowedPayload.TYPE, BhopAllowedPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BhopSettingsPayload.TYPE, BhopSettingsPayload.CODEC);

		// Writes minebhop-server.json on a dedicated server's first start, so owners can find it.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.isDedicatedServer()) {
				ConfigManager.readServerSettings(serverFile());
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, BhopAllowedPayload.TYPE)) {
				sender.sendPacket(new BhopAllowedPayload());
			}
			if (server.isDedicatedServer() && ServerPlayNetworking.canSend(handler, BhopSettingsPayload.TYPE)) {
				String settings = ConfigManager.readServerSettings(serverFile());
				if (settings != null) {
					sender.sendPacket(new BhopSettingsPayload(settings));
				}
			}
		});
	}

	private static Path serverFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.SERVER_FILE);
	}
}
