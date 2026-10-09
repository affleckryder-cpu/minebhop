package com.minebhop.fabric;

import com.minebhop.net.BhopAllowedPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Runs on both sides. On a server -- dedicated, or the integrated one behind a LAN world -- it
 * tells every joining player who has the mod that Source movement is allowed. Players without
 * the mod never get the payload, so vanilla clients join as normal.
 */
public class MineBhopFabricCommon implements ModInitializer {

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(BhopAllowedPayload.TYPE, BhopAllowedPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, BhopAllowedPayload.TYPE)) {
				sender.sendPacket(new BhopAllowedPayload());
			}
		});
	}
}
