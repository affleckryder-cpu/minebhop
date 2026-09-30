package com.minebhop.net;

import com.minebhop.MineBhop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent by a server running MineBhop to each joining player that has the mod: "Source movement is
 * allowed here." The client then runs it without the player having to opt in.
 *
 * <p>Deliberately empty, so anything that can send a raw plugin message on {@code minebhop:allowed}
 * -- a Paper plugin, say -- can grant it too.
 */
public record BhopAllowedPayload() implements CustomPacketPayload {
	public static final Type<BhopAllowedPayload> TYPE = new Type<>(MineBhop.id("allowed"));
	public static final StreamCodec<ByteBuf, BhopAllowedPayload> CODEC = StreamCodec.unit(new BhopAllowedPayload());

	@Override
	public Type<BhopAllowedPayload> type() {
		return TYPE;
	}
}
