package com.minebhop.fabric;

import com.minebhop.MineBhop;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;

/**
 * Runs on both sides. On a server -- dedicated, or the integrated one behind a LAN world -- it
 * tells every joining player who has the mod that Source movement is allowed. Players without
 * the mod never get the packet, so vanilla clients join as normal.
 *
 * <p>1.20.1 predates typed payloads: a packet is a channel name and raw bytes, here none.
 */
public class MineBhopFabricCommon implements ModInitializer {

	static final ResourceLocation ALLOWED = MineBhop.id("allowed");

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, ALLOWED)) {
				sender.sendPacket(ALLOWED, PacketByteBufs.empty());
			}
		});
	}
}
