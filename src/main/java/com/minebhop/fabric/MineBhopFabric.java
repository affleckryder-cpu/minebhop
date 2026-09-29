package com.minebhop.fabric;

import com.minebhop.MineBhop;
import com.minebhop.command.BhopCommand;
import com.minebhop.config.BhopMode;
import com.minebhop.hud.SpeedHud;
import com.minebhop.movement.SourceMoveHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Fabric entrypoint. All of the movement logic lives in the loader-agnostic packages. */
public class MineBhopFabric implements ClientModInitializer {

	private static KeyMapping toggleKey;
	private static KeyMapping hudKey;
	private static KeyMapping modeKey;

	@Override
	public void onInitializeClient() {
		MineBhop.init(FabricLoader.getInstance().getConfigDir().resolve("minebhop.json"));

		KeyMapping.Category category = KeyMapping.Category.register(MineBhop.id("main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minebhop.toggle", InputConstants.KEY_RBRACKET, category));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minebhop.hud", InputConstants.KEY_LBRACKET, category));
		modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minebhop.mode", InputConstants.UNKNOWN.getValue(), category));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
				BhopCommand.register(dispatcher, new BhopCommand.Feedback<FabricClientCommandSource>() {
					@Override
					public void send(FabricClientCommandSource source, String message) {
						source.sendFeedback(Component.literal(message));
					}

					@Override
					public void error(FabricClientCommandSource source, String message) {
						source.sendError(Component.literal(message));
					}
				}));

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS, MineBhop.id("speedometer"), new SpeedHud());

		ClientTickEvents.END_CLIENT_TICK.register(MineBhopFabric::onEndTick);
	}

	private static void onEndTick(Minecraft client) {
		if (client.player == null) {
			// Between worlds: drop the hop chain and the stale yaw sample.
			MineBhop.state().reset();
			SourceMoveHandler.resetYawTracking();
			return;
		}

		while (toggleKey.consumeClick()) {
			MineBhop.config().enabled = !MineBhop.config().enabled;
			MineBhop.state().reset();
			MineBhop.configManager().save();
			feedback(client, "Source movement " + onOff(MineBhop.config().enabled));
		}

		while (hudKey.consumeClick()) {
			MineBhop.config().hud = !MineBhop.config().hud;
			MineBhop.configManager().save();
			feedback(client, "HUD " + onOff(MineBhop.config().hud));
		}

		while (modeKey.consumeClick()) {
			MineBhop.config().bhopMode = MineBhop.config().bhopMode == BhopMode.AUTO ? BhopMode.MANUAL : BhopMode.AUTO;
			MineBhop.configManager().save();
			feedback(client, "Hop mode: " + MineBhop.config().bhopMode);
		}
	}

	private static String onOff(boolean value) {
		return value ? "enabled" : "disabled";
	}

	private static void feedback(Minecraft client, String message) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[MineBhop] " + message));
		}
	}
}
