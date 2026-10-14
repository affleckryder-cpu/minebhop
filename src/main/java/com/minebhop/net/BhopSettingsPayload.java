package com.minebhop.net;

import com.minebhop.MineBhop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent by a dedicated server that syncs its settings, to each joining player that has the mod: the
 * physics to use while connected, as JSON of setting to value (what a preset holds). Implies
 * {@link BhopAllowedPayload}.
 *
 * <p>On the wire it is one UTF-8 string, length-prefixed, so a server that cannot use this class
 * can still send it.
 */
public record BhopSettingsPayload(String json) implements CustomPacketPayload {

	public static final Type<BhopSettingsPayload> TYPE = new Type<>(MineBhop.id("settings"));
	public static final StreamCodec<ByteBuf, BhopSettingsPayload> CODEC =
			ByteBufCodecs.STRING_UTF8.map(BhopSettingsPayload::new, BhopSettingsPayload::json);

	@Override
	public Type<BhopSettingsPayload> type() {
		return TYPE;
	}
}
