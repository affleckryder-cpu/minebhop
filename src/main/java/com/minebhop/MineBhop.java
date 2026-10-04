package com.minebhop;

import com.minebhop.config.BhopConfig;
import com.minebhop.config.ConfigManager;
import com.minebhop.movement.MovementState;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

/**
 * Loader-agnostic holder for the mod's config and movement state.
 *
 * <p>Deliberately free of Fabric and NeoForge types. The movement code and every mixin reach the
 * config through here, which is what lets both loaders share the same sources -- each one supplies
 * its own entrypoint ({@code com.minebhop.fabric.MineBhopFabric},
 * {@code com.minebhop.neoforge.MineBhopNeoForge}) and calls {@link #init} during startup.
 */
public final class MineBhop {
	public static final String MOD_ID = "minebhop";

	private static final MovementState STATE = new MovementState();
	private static ConfigManager config;

	private MineBhop() {
	}

	/** Called once by the loader entrypoint with the platform's config directory. */
	public static void init(Path configFile) {
		config = new ConfigManager(configFile);
		config.load();
	}

	public static BhopConfig config() {
		ConfigManager manager = config;
		// Mixins can fire before the entrypoint has run in some startup orders; defaults keep the
		// movement code from having to null-check on every tick.
		return manager != null ? manager.get() : FALLBACK;
	}

	public static ConfigManager configManager() {
		return config;
	}

	public static MovementState state() {
		return STATE;
	}

	private static boolean menuRequested;

	/**
	 * Asks for the settings screen to open on the next client tick. A command cannot open it
	 * directly: the chat screen it was typed into closes right after, taking the new screen with it.
	 */
	public static void requestMenu() {
		menuRequested = true;
	}

	/** True once per {@link #requestMenu()}. */
	public static boolean consumeMenuRequest() {
		boolean requested = menuRequested;
		menuRequested = false;
		return requested;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static final BhopConfig FALLBACK = new BhopConfig();
}
