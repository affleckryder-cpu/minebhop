package com.minebhop.neoforge;

import com.minebhop.MineBhop;
import com.minebhop.command.BhopCommand;
import com.minebhop.config.BhopMode;
import com.minebhop.gui.BhopConfigScreen;
import com.minebhop.hud.SpeedometerRenderer;
import com.minebhop.movement.SourceMoveHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * NeoForge client entrypoint for Minecraft 1.21.1. Registration only; everything that moves the
 * player is shared with the Fabric build and, through port.gradle, with 26.x.
 */
@Mod(value = MineBhop.MOD_ID, dist = Dist.CLIENT)
public class MineBhopNeoForge {

	private static final String CATEGORY = "key.category.minebhop.main";

	static final KeyMapping TOGGLE_KEY = new KeyMapping("key.minebhop.toggle", InputConstants.KEY_RBRACKET, CATEGORY);
	static final KeyMapping HUD_KEY = new KeyMapping("key.minebhop.hud", InputConstants.KEY_LBRACKET, CATEGORY);
	static final KeyMapping MODE_KEY = new KeyMapping("key.minebhop.mode", InputConstants.UNKNOWN.getValue(), CATEGORY);
	static final KeyMapping MENU_KEY = new KeyMapping("key.minebhop.menu", InputConstants.KEY_BACKSLASH, CATEGORY);

	public MineBhopNeoForge(IEventBus modEventBus, ModContainer container) {
		MineBhop.init(FMLPaths.CONFIGDIR.get().resolve("minebhop.json"));

		modEventBus.addListener(MineBhopNeoForge::onRegisterKeyMappings);
		modEventBus.addListener(MineBhopNeoForge::onRegisterGuiLayers);

		// The Config button on the mods list.
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, parent) -> new BhopConfigScreen(parent));
	}

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(TOGGLE_KEY);
		event.register(HUD_KEY);
		event.register(MODE_KEY);
		event.register(MENU_KEY);
	}

	private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
		// Above the experience level, the last of vanilla's XP layers, so the speed readout sits
		// on top of the bar. The level number itself is hidden by GuiExperienceLevelMixin.
		event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, MineBhop.id("hud"), (graphics, deltaTracker) -> {
			SpeedometerRenderer.draw(graphics, deltaTracker);
			SpeedometerRenderer.drawXpBar(graphics, deltaTracker);
		});
	}

	/** Game bus: command registration and per-tick keybind handling. */
	@EventBusSubscriber(modid = MineBhop.MOD_ID, value = Dist.CLIENT)
	public static final class GameBus {
		private GameBus() {
		}

		@SubscribeEvent
		static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
			BhopCommand.register(event.getDispatcher(), new BhopCommand.Feedback<CommandSourceStack>() {
				@Override
				public void send(CommandSourceStack source, String message) {
					source.sendSuccess(() -> Component.literal(message), false);
				}

				@Override
				public void error(CommandSourceStack source, String message) {
					source.sendFailure(Component.literal(message));
				}
			});
		}

		@SubscribeEvent
		static void onClientTick(ClientTickEvent.Post event) {
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) {
				// Between worlds: drop the hop chain and the stale yaw sample.
				MineBhop.state().reset();
				SourceMoveHandler.resetForNewWorld();
				return;
			}

			while (MENU_KEY.consumeClick()) {
				MineBhop.requestMenu();
			}
			if (MineBhop.consumeMenuRequest()) {
				client.setScreen(new BhopConfigScreen(null));
			}

			while (TOGGLE_KEY.consumeClick()) {
				MineBhop.config().enabled = !MineBhop.config().enabled;
				MineBhop.state().reset();
				MineBhop.configManager().save();
				feedback(client, "Source movement " + onOff(MineBhop.config().enabled));
			}

			while (HUD_KEY.consumeClick()) {
				MineBhop.config().hud = !MineBhop.config().hud;
				MineBhop.configManager().save();
				feedback(client, "HUD " + onOff(MineBhop.config().hud));
			}

			while (MODE_KEY.consumeClick()) {
				MineBhop.config().bhopMode =
						MineBhop.config().bhopMode == BhopMode.AUTO ? BhopMode.MANUAL : BhopMode.AUTO;
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
}
