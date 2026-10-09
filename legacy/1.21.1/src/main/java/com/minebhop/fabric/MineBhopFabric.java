package com.minebhop.fabric;

import com.minebhop.MineBhop;
import com.minebhop.command.BhopCommand;
import com.minebhop.config.BhopMode;
import com.minebhop.gui.BhopConfigScreen;
import com.minebhop.hud.SpeedometerRenderer;
import com.minebhop.movement.SourceMoveHandler;
import com.minebhop.net.BhopAllowedPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Fabric entrypoint for Minecraft 1.21.1. Does what the 26.x one does, through the Fabric API of
 * the time: string key categories, and a single HUD callback that draws after all of vanilla's
 * HUD (which is where the speed readout has to be to sit on top of the XP bar).
 */
public class MineBhopFabric implements ClientModInitializer {

	private static final String CATEGORY = "key.category.minebhop.main";

	private static KeyMapping toggleKey;
	private static KeyMapping hudKey;
	private static KeyMapping modeKey;
	private static KeyMapping menuKey;

	@Override
	public void onInitializeClient() {
		MineBhop.init(FabricLoader.getInstance().getConfigDir().resolve("minebhop.json"));

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.minebhop.toggle", InputConstants.KEY_RBRACKET, CATEGORY));
		hudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.minebhop.hud", InputConstants.KEY_LBRACKET, CATEGORY));
		modeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.minebhop.mode", InputConstants.UNKNOWN.getValue(), CATEGORY));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.minebhop.menu", InputConstants.KEY_BACKSLASH, CATEGORY));

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

		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			SpeedometerRenderer.draw(graphics, deltaTracker);
			SpeedometerRenderer.drawXpBar(graphics, deltaTracker);
		});

		ClientTickEvents.END_CLIENT_TICK.register(MineBhopFabric::onEndTick);

		ClientPlayNetworking.registerGlobalReceiver(BhopAllowedPayload.TYPE,
				(payload, context) -> SourceMoveHandler.markServerAllowed());
	}

	private static void onEndTick(Minecraft client) {
		if (client.player == null) {
			// Between worlds: drop the hop chain and the stale yaw sample.
			MineBhop.state().reset();
			SourceMoveHandler.resetForNewWorld();
			return;
		}

		while (menuKey.consumeClick()) {
			MineBhop.requestMenu();
		}
		if (MineBhop.consumeMenuRequest()) {
			client.setScreen(new BhopConfigScreen(null));
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
