package com.minebhop.forge;

import com.minebhop.MineBhop;
import com.minebhop.movement.SourceMoveHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * Forge entrypoint for Minecraft 1.20.1, on both sides. On a server -- dedicated, or the integrated
 * one behind a LAN world -- it tells every joining player who has the mod that Source movement is
 * allowed. The channel is optional on both ends, so players without the mod join as normal and a
 * client with the mod can join servers without it.
 *
 * <p>Everything client-only is in {@link MineBhopForgeClient}, which is only touched on a client.
 */
@Mod(MineBhop.MOD_ID)
public class MineBhopForge {

	private static final ResourceLocation ALLOWED = MineBhop.id("allowed");
	private static final String PROTOCOL = "1";

	// A raw channel rather than a SimpleChannel: the packet is the channel name and no bytes, the
	// same thing the other builds send, where SimpleChannel would prefix a message id.
	private static final EventNetworkChannel CHANNEL = NetworkRegistry.newEventChannel(ALLOWED, () -> PROTOCOL,
			NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

	public MineBhopForge() {
		CHANNEL.addListener(MineBhopForge::onAllowed);
		MinecraftForge.EVENT_BUS.addListener(MineBhopForge::onPlayerLoggedIn);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			MineBhopForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
		}
	}

	/** Only ever fired on a client, so the client-only class it names is never loaded on a server. */
	private static void onAllowed(NetworkEvent.ServerCustomPayloadEvent event) {
		SourceMoveHandler.markServerAllowed();
		event.getSource().get().setPacketHandled(true);
	}

	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && CHANNEL.isRemotePresent(player.connection.connection)) {
			player.connection.send(new ClientboundCustomPayloadPacket(ALLOWED, new FriendlyByteBuf(Unpooled.buffer())));
		}
	}
}
