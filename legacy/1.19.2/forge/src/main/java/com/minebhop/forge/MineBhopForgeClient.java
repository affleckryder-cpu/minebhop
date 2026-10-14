package com.minebhop.forge;

import com.minebhop.MineBhop;
import com.minebhop.command.BhopCommand;
import com.minebhop.config.BhopMode;
import com.minebhop.gui.BhopConfigScreen;
import com.minebhop.gui.GuiGraphics;
import com.minebhop.hud.SpeedometerRenderer;
import com.minebhop.movement.SourceMoveHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * The client half of the Forge build for Minecraft 1.19.2. Registration only; everything that
 * moves the player is shared with the Fabric build and, through port.gradle, with 26.x.
 */
final class MineBhopForgeClient {

	private static final String CATEGORY = "key.category.minebhop.main";

	private static final KeyMapping TOGGLE_KEY = new KeyMapping("key.minebhop.toggle", InputConstants.KEY_RBRACKET, CATEGORY);
	private static final KeyMapping HUD_KEY = new KeyMapping("key.minebhop.hud", InputConstants.KEY_LBRACKET, CATEGORY);
	private static final KeyMapping MODE_KEY = new KeyMapping("key.minebhop.mode", InputConstants.UNKNOWN.getValue(), CATEGORY);
	private static final KeyMapping MENU_KEY = new KeyMapping("key.minebhop.menu", InputConstants.KEY_BACKSLASH, CATEGORY);

	private MineBhopForgeClient() {
	}

	static void init(IEventBus modEventBus) {
		MineBhop.init(FMLPaths.CONFIGDIR.get().resolve("minebhop.json"));

		modEventBus.addListener(MineBhopForgeClient::onRegisterKeyMappings);
		modEventBus.addListener(MineBhopForgeClient::onRegisterGuiOverlays);
		MinecraftForge.EVENT_BUS.addListener(MineBhopForgeClient::onRegisterClientCommands);
		MinecraftForge.EVENT_BUS.addListener(MineBhopForgeClient::onClientTick);

		// The Config button on the mods list.
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> new BhopConfigScreen(parent)));
	}

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(TOGGLE_KEY);
		event.register(HUD_KEY);
		event.register(MODE_KEY);
		event.register(MENU_KEY);
	}

	private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
		// Above the experience bar, so the speed readout sits on top of it. The level number that
		// vanilla draws with the bar is hidden by GuiExperienceLevelMixin.
		event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "hud", (gui, pose, partialTick, width, height) -> {
			GuiGraphics graphics = new GuiGraphics(pose);
			SpeedometerRenderer.draw(graphics, partialTick);
			SpeedometerRenderer.drawXpBar(graphics, partialTick);
		});
	}

	private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		BhopCommand.register(event.getDispatcher(), new BhopCommand.Feedback<CommandSourceStack>() {
			@Override
			public void send(CommandSourceStack source, String message) {
				source.sendSuccess(Component.literal(message), false);
			}

			@Override
			public void error(CommandSourceStack source, String message) {
				source.sendFailure(Component.literal(message));
			}
		});
	}

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
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
