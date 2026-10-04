package com.minebhop.gui;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
import com.minebhop.config.BhopMode;
import com.minebhop.config.ConfigManager;
import com.minebhop.config.Tunable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The settings screen: every {@link Tunable} field as a row, grouped by section, with the presets
 * along the top.
 *
 * <p>Built by reflection over {@link BhopConfig}, the same way the {@code /bhop} command is, so a
 * new setting shows up here without touching this class. Values go through
 * {@link ConfigManager#set}, so the screen and the command parse and validate identically. Changes
 * apply as you make them and are saved when the screen closes.
 */
public class BhopConfigScreen extends Screen {

	private static final int WIDGET_WIDTH = 100;
	private static final int ROW_WIDTH = 320;
	private static final int LIST_TOP = 50;
	private static final int FOOTER = 34;
	private static final int COLOUR_VALID = 0xFFE0E0E0;
	private static final int COLOUR_INVALID = 0xFFFF5C6C;

	private final Screen parent;

	public BhopConfigScreen(Screen parent) {
		super(Component.literal("MineBhop Settings"));
		this.parent = parent;
	}

	/**
	 * Opens {@code screen}. 26.1.x has {@code Minecraft#setScreen}; 26.2 moved it to {@code Gui}.
	 * The same sources build for both, so the method is resolved by name -- Minecraft 26.x ships
	 * unobfuscated, so the name is the same in development and in production.
	 */
	public static void show(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		try {
			Object owner;
			try {
				Minecraft.class.getMethod("setScreen", Screen.class);
				owner = client;
			} catch (NoSuchMethodException e) {
				owner = client.gui;
			}
			owner.getClass().getMethod("setScreen", Screen.class).invoke(owner, screen);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not open screen", e);
		}
	}

	@Override
	protected void init() {
		// Presets and reset, centred along the top.
		String[] presets = BhopConfig.presetNames();
		int buttonWidth = 50;
		int gap = 4;
		int count = presets.length + 1;
		int x = (width - (count * buttonWidth + (count - 1) * gap)) / 2;
		for (String preset : presets) {
			addRenderableWidget(Button.builder(Component.literal(preset), button -> {
				MineBhop.config().applyPreset(preset);
				rebuildWidgets();
			}).bounds(x, 24, buttonWidth, 20).tooltip(Tooltip.create(Component.literal("Apply the " + preset + " preset"))).build());
			x += buttonWidth + gap;
		}
		addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
			MineBhop.configManager().reset();
			rebuildWidgets();
		}).bounds(x, 24, buttonWidth, 20).tooltip(Tooltip.create(Component.literal("Every setting back to default"))).build());

		addRenderableWidget(new SettingsList());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 27, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		MineBhop.configManager().save();
		if (parent != null) {
			show(parent);
		} else {
			super.onClose();
		}
	}

	/** "airAcceleration" -> "Air Acceleration". */
	private static String label(String key) {
		String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private final class SettingsList extends ContainerObjectSelectionList<Row> {
		SettingsList() {
			super(BhopConfigScreen.this.minecraft, BhopConfigScreen.this.width,
					BhopConfigScreen.this.height - LIST_TOP - FOOTER, LIST_TOP, 24);
			for (Field field : BhopConfig.class.getDeclaredFields()) {
				Tunable tunable = field.getAnnotation(Tunable.class);
				if (tunable == null) {
					continue;
				}
				if (!tunable.section().isEmpty()) {
					addEntry(new HeaderRow(tunable.section()));
				}
				addEntry(new SettingRow(field, tunable));
			}
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}
	}

	private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
	}

	private final class HeaderRow extends Row {
		private final String title;

		HeaderRow(String title) {
			this.title = title;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			graphics.centeredText(font, title, getContentXMiddle(), getContentYMiddle() - 2, 0xFF5CE1FF);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	private final class SettingRow extends Row {
		private final String name;
		private final String unit;
		private final AbstractWidget widget;

		SettingRow(Field field, Tunable tunable) {
			String key = field.getName();
			this.name = label(key);
			this.unit = tunable.unit();
			this.widget = createWidget(field, key);

			String cvar = tunable.cvar().isEmpty() ? "" : "\n\nSource cvar: " + tunable.cvar();
			widget.setTooltip(Tooltip.create(Component.literal(tunable.value() + cvar + "\n\n/bhop set " + key)));
		}

		private AbstractWidget createWidget(Field field, String key) {
			ConfigManager manager = MineBhop.configManager();
			Object current;
			try {
				current = field.get(manager.get());
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}

			Class<?> type = field.getType();
			Component title = Component.literal(label(key));
			if (type == boolean.class) {
				return CycleButton.onOffBuilder((Boolean) current).displayOnlyValue()
						.create(0, 0, WIDGET_WIDTH, 20, title, (button, value) -> manager.set(key, value.toString()));
			}
			if (type == BhopMode.class) {
				return CycleButton.<BhopMode>builder(mode -> Component.literal(mode.name()), (BhopMode) current)
						.withValues(BhopMode.values()).displayOnlyValue()
						.create(0, 0, WIDGET_WIDTH, 20, title, (button, value) -> manager.set(key, value.name()));
			}

			EditBox box = new EditBox(font, 0, 0, WIDGET_WIDTH, 20, title);
			box.setValue(String.valueOf(current));
			// Applied as you type; a value that does not parse just turns red and is not applied.
			box.setResponder(text -> box.setTextColor(manager.set(key, text) == null ? COLOUR_VALID : COLOUR_INVALID));
			return box;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int textY = getContentYMiddle() - 4;
			graphics.text(font, name, getContentX(), textY, 0xFFFFFFFF, false);
			if (!unit.isEmpty()) {
				graphics.text(font, unit, getContentX() + font.width(name) + 5, textY, 0xFF8A8F98, false);
			}
			widget.setX(getContentRight() - WIDGET_WIDTH);
			widget.setY(getContentY());
			widget.extractRenderState(graphics, mouseX, mouseY, delta);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(widget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(widget);
		}
	}
}
