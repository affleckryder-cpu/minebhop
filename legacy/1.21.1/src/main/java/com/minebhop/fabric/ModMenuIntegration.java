package com.minebhop.fabric;

import com.minebhop.gui.BhopConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Gives MineBhop a settings button in Mod Menu's mods list.
 *
 * <p>Mod Menu is optional. Nothing else references this class -- only Mod Menu loads it, through
 * the {@code modmenu} entrypoint -- so without Mod Menu installed it is never touched.
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BhopConfigScreen::new;
	}
}
