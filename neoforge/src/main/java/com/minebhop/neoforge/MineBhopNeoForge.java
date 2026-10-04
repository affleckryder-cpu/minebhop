package com.minebhop.neoforge;

import com.minebhop.MineBhop;
import com.minebhop.command.BhopCommand;
import com.minebhop.config.BhopMode;
import com.minebhop.gui.BhopConfigScreen;
import com.minebhop.hud.SpeedometerRenderer;
import com.minebhop.movement.SourceMoveHandler;
import com.minebhop.net.BhopAllowedPayload;
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
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * NeoForge entrypoint.
 *
 * <p>Everything that actually moves the player -- the Source simulation, the config, the command
 * tree, all four mixins -- is shared verbatim with the Fabric build. Both loaders run on Mojang
 * mappings in 26.2, so only registration differs, and that is all this class does.
 *
 * <p>Registration is split by bus. Key mappings and GUI layers are {@code IModBusEvent}s, so they
 * are wired to the mod bus handed to the constructor; commands and ticking are game bus events and
 * go through {@link EventBusSubscriber}, which targets the game bus in this NeoForge version.
 */
@Mod(value = MineBhop.MOD_ID, dist = Dist.CLIENT)
public class MineBhopNeoForge {

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MineBhop.id("main"));

	static final KeyMapping TOGGLE_KEY = new KeyMapping(
			"key.minebhop.toggle", InputConstants.KEY_RBRACKET, CATEGORY);
	static final KeyMapping HUD_KEY = new KeyMapping(
			"key.minebhop.hud", InputConstants.KEY_LBRACKET, CATEGORY);
	static final KeyMapping MODE_KEY = new KeyMapping(
			"key.minebhop.mode", InputConstants.UNKNOWN.getValue(), CATEGORY);
	static final KeyMapping MENU_KEY = new KeyMapping(
			"key.minebhop.menu", InputConstants.KEY_BACKSLASH, CATEGORY);

	public MineBhopNeoForge(IEventBus modEventBus, ModContainer container) {
		MineBhop.init(FMLPaths.CONFIGDIR.get().resolve("minebhop.json"));

		modEventBus.addListener(MineBhopNeoForge::onRegisterKeyMappings);
		modEventBus.addListener(MineBhopNeoForge::onRegisterGuiLayers);
		modEventBus.addListener(MineBhopNeoForge::onRegisterPayloadHandlers);

		// The Config button on the mods list.
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(IConfigScreenFactory) (modContainer, parent) -> new BhopConfigScreen(parent));
	}

	private static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
		event.register(BhopAllowedPayload.TYPE, (payload, context) -> SourceMoveHandler.markServerAllowed());
	}

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		event.registerCategory(CATEGORY);
		event.register(TOGGLE_KEY);
		event.register(HUD_KEY);
		event.register(MODE_KEY);
		event.register(MENU_KEY);
	}

	private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
		// CAMERA_OVERLAYS is NeoForge's equivalent of the slot Fabric calls MISC_OVERLAYS.
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, MineBhop.id("speedometer"),
				new NeoForgeSpeedHud());
		event.registerAbove(VanillaGuiLayers.CONTEXTUAL_INFO_BAR, MineBhop.id("xp_bar_speed"),
				SpeedometerRenderer::drawXpBar);
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

		/** While speed is on the XP bar, vanilla's level number would show through next to ours. */
		@SubscribeEvent
		static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
			if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL) && SpeedometerRenderer.xpBarActive()) {
				event.setCanceled(true);
			}
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
				BhopConfigScreen.show(new BhopConfigScreen(null));
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
