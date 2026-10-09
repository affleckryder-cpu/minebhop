package com.minebhop.gui;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
import com.minebhop.config.BhopMode;
import com.minebhop.config.ConfigManager;
import com.minebhop.config.Range;
import com.minebhop.config.Tunable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The settings screen: every {@link Tunable} field as a row, grouped by section, with the presets
 * along the top.
 *
 * <p>Built by reflection over {@link BhopConfig}, the same way the {@code /bhop} command is, so a
 * new setting shows up here without touching this class. Values go through
 * {@link ConfigManager#set}, so the screen and the command parse and validate identically. Changes
 * apply as you make them and are saved when the screen closes.
 *
 * <p>The widgets are drawn flat, in the HUD panel's palette, rather than with vanilla's button
 * sprites: {@link FlatButton} and {@link Switch} override only how a button looks, so focus,
 * narration, sounds and keyboard navigation are still vanilla's.
 *
 * <p>This is the Minecraft 1.21.1 version. It behaves the same as the 26.x screen and is kept in
 * step with it by hand; it is a separate file because widgets are drawn through different methods
 * here ({@code renderWidget} rather than {@code extractContents}) and list rows are handed their
 * position instead of asking for it.
 */
public class BhopConfigScreen extends Screen {

	private static final int WIDGET_WIDTH = 110;
	private static final int ROW_WIDTH = 350;
	private static final int LIST_TOP = 54;
	private static final int DROPDOWN_WIDTH = 120;
	private static final int FOOTER = 34;

	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFF8A8F98;
	private static final int ACCENT = 0xFF5CE1FF;
	private static final int ACCENT_HOT = 0xFF8CEBFF;
	private static final int ON_ACCENT = 0xFF0B0D12;
	private static final int INVALID = 0xFFFF5C6C;
	private static final int PANEL = 0xB0101218;
	private static final int MENU = 0xF4141720;
	private static final int SURFACE = 0xFF20242E;
	private static final int SURFACE_HOT = 0xFF2C323F;
	private static final int ROW_HOT = 0x14FFFFFF;
	private static final int RULE = 0x30FFFFFF;
	private static final int DANGER = 0xFF8A2F3A;

	private final Screen parent;
	private boolean dropdownOpen;
	/** Set by the dropdown's own buttons, so the click that used them does not also close it. */
	private boolean dropdownClicked;

	public BhopConfigScreen(Screen parent) {
		super(Component.literal("MineBhop Settings"));
		this.parent = parent;
	}

	public static void show(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	@Override
	protected void init() {
		ConfigManager manager = MineBhop.configManager();

		// One row along the top: presets dropdown, reset, then a name box and Save for custom ones.
		int y = 26;
		int x = (width - 322) / 2;
		addRenderableWidget(new FlatButton(x, y, DROPDOWN_WIDTH, 20,
				Component.literal(dropdownOpen ? "Presets  ▴" : "Presets  ▾"), FlatButton.Style.NORMAL, button -> {
			dropdownOpen = !dropdownOpen;
			dropdownClicked = true;
			rebuildWidgets();
		}));
		FlatButton reset = addRenderableWidget(new FlatButton(x + 124, y, 50, 20, Component.literal("Reset"),
				FlatButton.Style.NORMAL, button -> {
			manager.reset();
			rebuildWidgets();
		}));
		reset.setTooltip(Tooltip.create(Component.literal("Every setting back to default")));

		boolean[] nameInvalid = { false };
		EditBox name = new EditBox(font, x + 189, y + 6, 80, 14, Component.literal("Preset name"));
		name.setBordered(false);
		name.setMaxLength(16);
		name.setHint(Component.literal("new preset..."));
		name.setTooltip(Tooltip.create(Component.literal(
				"Save your current physics settings as a preset. Everything outside General is stored.")));
		name.setResponder(text -> nameInvalid[0] = false);
		int boxLeft = x + 184;
		addRenderableOnly((graphics, mouseX, mouseY, delta) ->
				field(graphics, boxLeft, y, 90, name.isFocused(), nameInvalid[0]));
		addRenderableWidget(name);
		addRenderableWidget(new FlatButton(x + 278, y, 44, 20, Component.literal("Save"), FlatButton.Style.NORMAL, button -> {
			if (manager.savePreset(name.getValue()) != null) {
				nameInvalid[0] = true;
				return;
			}
			rebuildWidgets();
		}));

		// The open dropdown overlaps the list. Screens hit-test children in the order they were
		// added and draw them in that same order, so to be both clickable ahead of the list and
		// drawn over it, its items are added as listeners before the list and as renderables after.
		List<FlatButton> items = dropdownOpen ? dropdownItems(x, y + 22) : List.of();
		items.forEach(this::addWidget);

		addRenderableWidget(new SettingsList());

		addRenderableWidget(new FlatButton(width / 2 - 100, height - 27, 200, 20, CommonComponents.GUI_DONE,
				FlatButton.Style.PRIMARY, button -> onClose()));

		if (!items.isEmpty()) {
			int menuTop = y + 21;
			int menuHeight = items.get(items.size() - 1).getBottom() + 1 - menuTop;
			addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
				graphics.fill(x, menuTop, x + DROPDOWN_WIDTH, menuTop + menuHeight, MENU);
				graphics.renderOutline(x, menuTop, DROPDOWN_WIDTH, menuHeight, ACCENT);
			});
			items.forEach(this::addRenderableOnly);
		}
	}

	/**
	 * One button per preset, stacked under the dropdown button: built-in ones first, then saved ones,
	 * each saved one with a small delete button beside it.
	 */
	// ponytail: no scrolling -- the column runs off the screen past roughly (height - 80) / 18
	// presets. Add a scrolling list here if anyone actually saves that many.
	private List<FlatButton> dropdownItems(int x, int y) {
		ConfigManager manager = MineBhop.configManager();
		List<String> custom = manager.customPresetNames();
		List<FlatButton> items = new ArrayList<>();
		for (String preset : manager.presetNames()) {
			boolean deletable = custom.contains(preset);
			items.add(new FlatButton(x + 1, y, DROPDOWN_WIDTH - 2 - (deletable ? 18 : 0), 18,
					Component.literal(preset), FlatButton.Style.MENU, button -> {
				manager.applyPreset(preset);
				dropdownOpen = false;
				dropdownClicked = true;
				rebuildWidgets();
			}));
			if (deletable) {
				FlatButton delete = new FlatButton(x + DROPDOWN_WIDTH - 19, y, 18, 18, Component.literal("✕"),
						FlatButton.Style.DANGER, button -> {
					manager.deletePreset(preset);
					dropdownClicked = true;
					rebuildWidgets();
				});
				delete.setTooltip(Tooltip.create(Component.literal("Delete " + preset)));
				items.add(delete);
			}
			y += 18;
		}
		return items;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		dropdownClicked = false;
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		// A click anywhere else closes the dropdown, the way one is expected to behave.
		if (dropdownOpen && !dropdownClicked) {
			dropdownOpen = false;
			rebuildWidgets();
		}
		return handled;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		String brand = "MINEBHOP";
		String rest = "  SETTINGS";
		int left = (width - font.width(brand + rest)) / 2;
		graphics.drawString(font, brand, left, 10, ACCENT, false);
		graphics.drawString(font, rest, left + font.width(brand), 10, DIM, false);
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

	/** Background of a text field: a flat surface with an underline that shows focus or an error. */
	private static void field(GuiGraphics graphics, int x, int y, int width, boolean focused, boolean invalid) {
		graphics.fill(x, y, x + width, y + 20, SURFACE);
		graphics.fill(x, y + 19, x + width, y + 20, invalid ? INVALID : focused ? ACCENT : RULE);
	}

	// ------------------------------------------------------------------
	// Flat widgets
	// ------------------------------------------------------------------

	/** A button drawn as a flat surface instead of vanilla's sprite. */
	private static class FlatButton extends Button {
		enum Style {
			/** Dark surface, accent underline on hover. */
			NORMAL,
			/** Filled with the accent colour; the screen's main action. */
			PRIMARY,
			/** Transparent until hovered, label left-aligned; a dropdown item. */
			MENU,
			/** Transparent until hovered, then red; a delete button. */
			DANGER
		}

		private final Style style;

		FlatButton(int x, int y, int width, int height, Component message, Style style, OnPress onPress) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.style = style;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			boolean hot = isActive() && isHoveredOrFocused();
			int background = switch (style) {
				case NORMAL -> hot ? SURFACE_HOT : SURFACE;
				case PRIMARY -> hot ? ACCENT_HOT : ACCENT;
				case MENU -> hot ? SURFACE_HOT : 0;
				case DANGER -> hot ? DANGER : 0;
			};
			if (background != 0) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), background);
			}
			if (style == Style.NORMAL && hot) {
				graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), ACCENT);
			}

			int colour = !isActive() ? DIM
					: style == Style.PRIMARY ? ON_ACCENT
					: style == Style.DANGER && !hot ? DIM
					: TEXT;
			Font font = Minecraft.getInstance().font;
			int textX = style == Style.MENU ? getX() + 7 : getX() + (getWidth() - font.width(getMessage())) / 2;
			graphics.drawString(font, getMessage(), textX, getY() + (getHeight() - 8) / 2, colour, false);
		}
	}

	/** An on/off switch: a pill with a knob that sits right when on. */
	private static class Switch extends Button {
		private boolean on;

		Switch(int width, boolean on, Component title, Consumer<Boolean> onChange) {
			super(0, 0, width, 20, title, button -> {
				Switch self = (Switch) button;
				self.on = !self.on;
				onChange.accept(self.on);
			}, DEFAULT_NARRATION);
			this.on = on;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			boolean hot = isHoveredOrFocused();
			int trackLeft = getRight() - 26;
			int trackTop = getY() + 4;
			graphics.fill(trackLeft, trackTop, trackLeft + 26, trackTop + 12,
					on ? (hot ? ACCENT_HOT : ACCENT) : (hot ? SURFACE_HOT : SURFACE));
			int knobLeft = on ? trackLeft + 15 : trackLeft + 1;
			graphics.fill(knobLeft, trackTop + 1, knobLeft + 10, trackTop + 11, on ? ON_ACCENT : DIM);

			Font font = Minecraft.getInstance().font;
			String state = on ? "ON" : "OFF";
			graphics.drawString(font, state, trackLeft - 6 - font.width(state), getY() + 6, on ? ACCENT : DIM, false);
		}
	}

	/**
	 * A draggable bar for a ranged number. Dragging, clicking and the arrow keys are vanilla's; this
	 * only maps the 0..1 slider position onto the setting's range and draws it flat. The value
	 * itself is drawn by the row, to the left of the bar.
	 */
	private static class FlatSlider extends AbstractSliderButton {
		private final double min;
		private final double max;
		private final boolean whole;
		private final double step;
		private final Consumer<String> onChange;
		private String display;

		FlatSlider(int width, double min, double max, boolean whole, double current, Component title, Consumer<String> onChange) {
			super(0, 0, width, 20, title, (Math.max(min, Math.min(max, current)) - min) / (max - min));
			this.min = min;
			this.max = max;
			this.whole = whole;
			// Fine enough to be useful, coarse enough that a pixel of drag is a visible change.
			double span = max - min;
			this.step = whole ? 1.0 : span <= 5.0 ? 0.01 : span <= 50.0 ? 0.1 : 1.0;
			this.onChange = onChange;
			// Shows the stored value until it is dragged, so an exact default such as 301.993 is
			// not rounded just by opening the screen.
			this.display = format(current);
		}

		String display() {
			return display;
		}

		private String format(double number) {
			if (whole) {
				return String.valueOf(Math.round(number));
			}
			return String.format(Locale.ROOT, step == 0.01 ? "%.2f" : step == 0.1 ? "%.1f" : "%.0f", number);
		}

		@Override
		protected void updateMessage() {
		}

		@Override
		protected void applyValue() {
			double snapped = Math.round((min + value * (max - min)) / step) * step;
			display = format(Math.max(min, Math.min(max, snapped)));
			onChange.accept(display);
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			boolean hot = isHoveredOrFocused();
			// Vanilla maps the mouse onto x+4 .. right-4, so the track is drawn over exactly that.
			int left = getX() + 4;
			int right = getRight() - 4;
			int middle = getY() + getHeight() / 2;
			int knob = left + (int) Math.round(value * (right - left));
			graphics.fill(left, middle - 1, right, middle + 1, SURFACE_HOT);
			graphics.fill(left, middle - 1, knob, middle + 1, ACCENT);
			graphics.fill(knob - 3, middle - 6, knob + 3, middle + 6, hot ? ACCENT_HOT : TEXT);
		}
	}

	// ------------------------------------------------------------------
	// The list
	// ------------------------------------------------------------------

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

		/** One dark panel behind the rows, in place of vanilla's full-width darkened strip. */
		@Override
		protected void renderListBackground(GuiGraphics graphics) {
			graphics.fill(getRowLeft() - 10, getY(), getRowLeft() + getRowWidth() + 10, getBottom(), PANEL);
		}

		@Override
		protected void renderListSeparators(GuiGraphics graphics) {
		}
	}

	private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
	}

	private final class HeaderRow extends Row {
		private final String title;

		HeaderRow(String title) {
			this.title = title.toUpperCase(Locale.ROOT);
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovered, float delta) {
			int textY = top + height - 11;
			graphics.drawString(font, title, left + 4, textY, ACCENT, false);
			graphics.fill(left + 4 + font.width(title) + 6, textY + 4, left + width - 4, textY + 5, RULE);
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
		/** Only for text settings: whether what is typed right now fails to parse. */
		private boolean invalid;

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
				return new Switch(WIDGET_WIDTH, (Boolean) current, title, value -> manager.set(key, value.toString()));
			}
			if (type == BhopMode.class) {
				return new FlatButton(0, 0, WIDGET_WIDTH, 20, Component.literal(((BhopMode) current).name()),
						FlatButton.Style.NORMAL, button -> {
					BhopMode[] modes = BhopMode.values();
					BhopMode next = modes[(BhopMode.valueOf(button.getMessage().getString()).ordinal() + 1) % modes.length];
					manager.set(key, next.name());
					button.setMessage(Component.literal(next.name()));
				});
			}

			Range range = field.getAnnotation(Range.class);
			if (range != null) {
				return new FlatSlider(WIDGET_WIDTH, range.min(), range.max(), type == int.class,
						((Number) current).doubleValue(), title, value -> manager.set(key, value));
			}

			// A number with no declared range falls back to a text box. Unbordered: render
			// draws the flat field behind it.
			EditBox box = new EditBox(font, 0, 0, WIDGET_WIDTH - 10, 14, title);
			box.setBordered(false);
			box.setValue(String.valueOf(current));
			// Applied as you type; a value that does not parse is flagged and not applied.
			box.setResponder(text -> invalid = manager.set(key, text) != null);
			return box;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovered, float delta) {
			if (hovered) {
				graphics.fill(left - 2, top - 2, left + width + 2, top + height + 2, ROW_HOT);
			}
			int textY = top + height / 2 - 4;
			graphics.drawString(font, name, left + 4, textY, TEXT, false);
			if (!unit.isEmpty()) {
				graphics.drawString(font, unit, left + 4 + font.width(name) + 5, textY, DIM, false);
			}

			int widgetLeft = left + width - WIDGET_WIDTH - 4;
			if (widget instanceof EditBox) {
				field(graphics, widgetLeft, top, WIDGET_WIDTH, widget.isFocused(), invalid);
				widget.setX(widgetLeft + 5);
				widget.setY(top + 6);
			} else {
				widget.setX(widgetLeft);
				widget.setY(top);
			}
			if (widget instanceof FlatSlider slider) {
				String value = slider.display();
				graphics.drawString(font, value, widgetLeft - 4 - font.width(value), textY, ACCENT, false);
			}
			widget.render(graphics, mouseX, mouseY, delta);
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
