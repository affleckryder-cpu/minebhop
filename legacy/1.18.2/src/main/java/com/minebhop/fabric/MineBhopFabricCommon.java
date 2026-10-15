package com.minebhop.fabric;

import com.minebhop.MineBhop;
import com.minebhop.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/**
 * Runs on both sides. On a server -- dedicated, or the integrated one behind a LAN world -- it
 * tells every joining player who has the mod that Source movement is allowed. Players without
 * the mod never get the packet, so vanilla clients join as normal.
 *
 * <p>1.18.2 predates typed payloads: a packet is a channel name and raw bytes, here none.
 */
public class MineBhopFabricCommon implements ModInitializer {

	static final ResourceLocation ALLOWED = MineBhop.id("allowed");
	/** The server's physics, as one length-prefixed UTF-8 string of JSON; the same bytes as 26.x. */
	static final ResourceLocation SETTINGS = MineBhop.id("settings");

	@Override
	public void onInitialize() {
		// Writes minebhop-server.json on a dedicated server's first start, so owners can find it.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server.isDedicatedServer()) {
				ConfigManager.readServerSettings(serverFile());
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, ALLOWED)) {
				sender.sendPacket(ALLOWED, PacketByteBufs.empty());
			}
			if (server.isDedicatedServer() && ServerPlayNetworking.canSend(handler, SETTINGS)) {
				String settings = ConfigManager.readServerSettings(serverFile());
				if (settings != null) {
					sender.sendPacket(SETTINGS, PacketByteBufs.create().writeUtf(settings));
				}
			}
		});
	}

	private static Path serverFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(ConfigManager.SERVER_FILE);
	}
}
