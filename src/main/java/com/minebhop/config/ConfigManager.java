package com.minebhop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, saves and reflectively edits {@link BhopConfig}.
 *
 * <p>Editing goes through reflection over {@link Tunable} fields so that adding a knob to
 * {@code BhopConfig} automatically exposes it to {@code /bhop set}, {@code /bhop get},
 * {@code /bhop list} and the command completions with no further wiring.
 */
public final class ConfigManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("minebhop");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private BhopConfig config = new BhopConfig();

	/**
	 * @param file where the JSON lives. Passed in rather than looked up so this class stays free of
	 *             loader-specific APIs and can be shared by the Fabric and NeoForge builds.
	 */
	public ConfigManager(Path file) {
		this.file = file;
	}

	public BhopConfig get() {
		return config;
	}

	public Path file() {
		return file;
	}

	public void load() {
		loadPresets();
		if (!Files.exists(file)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			BhopConfig loaded = GSON.fromJson(reader, BhopConfig.class);
			if (loaded != null) {
				config = loaded;
				clampToRanges();
			}
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.error("Could not read {}, keeping defaults", file, e);
		}
	}

	/**
	 * Pulls every out-of-range number back inside its {@link Range}. {@link #set} refuses such
	 * values, but a config file can be edited by hand, or written by a version with wider limits.
	 */
	private void clampToRanges() {
		BhopConfig defaults = new BhopConfig();
		for (Field field : BhopConfig.class.getDeclaredFields()) {
			Range range = field.getAnnotation(Range.class);
			if (range == null) {
				continue;
			}
			try {
				double value = ((Number) field.get(config)).doubleValue();
				if (value >= range.min() && value <= range.max()) {
					continue;
				}
				double fixed = Double.isNaN(value)
						? ((Number) field.get(defaults)).doubleValue()
						: Math.max(range.min(), Math.min(range.max(), value));
				if (field.getType() == int.class) {
					field.setInt(config, (int) fixed);
				} else {
					field.setDouble(config, fixed);
				}
				LOGGER.warn("{} was {} in {}; limited to {}", field.getName(), value, file.getFileName(), trim(fixed));
			} catch (IllegalAccessException e) {
				LOGGER.error("Could not check {}", field.getName(), e);
			}
		}
	}

	/** "250" rather than "250.0". */
	private static String trim(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", file, e);
		}
	}

	public void reset() {
		config = new BhopConfig();
	}

	// ------------------------------------------------------------------
	// Custom presets
	// ------------------------------------------------------------------

	private Map<String, Map<String, String>> presets = new LinkedHashMap<>();

	/** Next to the config, in its own file so presets are easy to share. */
	private Path presetsFile() {
		return file.resolveSibling("minebhop-presets.json");
	}

	private void loadPresets() {
		if (!Files.exists(presetsFile())) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(presetsFile(), StandardCharsets.UTF_8)) {
			Map<String, Map<String, String>> loaded = GSON.fromJson(reader,
					new TypeToken<LinkedHashMap<String, LinkedHashMap<String, String>>>() { }.getType());
			if (loaded != null) {
				presets = loaded;
			}
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.error("Could not read {}, ignoring custom presets", presetsFile(), e);
		}
	}

	private void savePresets() {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(presetsFile(), StandardCharsets.UTF_8)) {
				GSON.toJson(presets, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", presetsFile(), e);
		}
	}

	/** Names of the presets the player has saved, in the order they were saved. */
	public List<String> customPresetNames() {
		return new ArrayList<>(presets.keySet());
	}

	/** Built-in presets followed by custom ones, for command completion. */
	public List<String> presetNames() {
		List<String> names = new ArrayList<>(List.of(BhopConfig.presetNames()));
		names.addAll(presets.keySet());
		return names;
	}

	/**
	 * Stores the current physics settings under {@code name}, replacing any custom preset already
	 * using it. A preset holds every setting outside the General section, so loading one never
	 * touches the HUD, jump stats or server switches.
	 *
	 * @return null on success, otherwise a human readable error
	 */
	public String savePreset(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		if (!key.matches("[a-z0-9_-]{1,16}")) {
			return "Preset names are 1-16 characters: letters, digits, _ and -";
		}
		// "save" and "delete" are subcommands of /bhop preset, so a preset by that name could never load.
		if (List.of(BhopConfig.presetNames()).contains(key) || key.equals("save") || key.equals("delete")) {
			return "'" + key + "' is taken; pick another name";
		}
		Map<String, String> values = new LinkedHashMap<>();
		boolean general = false;
		for (Field field : BhopConfig.class.getDeclaredFields()) {
			Tunable tunable = field.getAnnotation(Tunable.class);
			if (tunable == null) {
				continue;
			}
			if (!tunable.section().isEmpty()) {
				general = tunable.section().equals("General");
			}
			if (general) {
				continue;
			}
			try {
				values.put(field.getName(), String.valueOf(field.get(config)));
			} catch (IllegalAccessException e) {
				return "Could not read '" + field.getName() + "'";
			}
		}
		presets.put(key, values);
		savePresets();
		return null;
	}

	/** @return false if there is no custom preset by that name */
	public boolean deletePreset(String name) {
		if (presets.remove(name.toLowerCase(Locale.ROOT)) == null) {
			return false;
		}
		savePresets();
		return true;
	}

	/** Applies a built-in or custom preset. @return false if the name is unknown */
	public boolean applyPreset(String name) {
		if (config.applyPreset(name)) {
			return true;
		}
		Map<String, String> values = presets.get(name.toLowerCase(Locale.ROOT));
		if (values == null) {
			return false;
		}
		// Through set(), so a hand-edited or outdated file cannot put a bad value in: unknown keys
		// and unparseable values are simply skipped.
		values.forEach(this::set);
		return true;
	}

	// ------------------------------------------------------------------
	// Reflective access
	// ------------------------------------------------------------------

	/** All tunable keys, sorted, for command completion. */
	public static List<String> keys() {
		List<String> keys = new ArrayList<>();
		for (Field field : BhopConfig.class.getDeclaredFields()) {
			if (field.isAnnotationPresent(Tunable.class)) {
				keys.add(field.getName());
			}
		}
		return keys;
	}

	private static Field findField(String key) {
		for (Field field : BhopConfig.class.getDeclaredFields()) {
			if (field.isAnnotationPresent(Tunable.class) && field.getName().equalsIgnoreCase(key)) {
				return field;
			}
		}
		return null;
	}

	/** Formatted {@code key = value} plus unit, or null if the key does not exist. */
	public String describeValue(String key) {
		Field field = findField(key);
		if (field == null) {
			return null;
		}
		try {
			Tunable tunable = field.getAnnotation(Tunable.class);
			String unit = tunable.unit().isEmpty() ? "" : " " + tunable.unit();
			return field.getName() + " = " + field.get(config) + unit;
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	/** The doc string for a key, with the matching Source cvar appended when there is one. */
	public static String describeKey(String key) {
		Field field = findField(key);
		if (field == null) {
			return null;
		}
		Tunable tunable = field.getAnnotation(Tunable.class);
		String cvar = tunable.cvar().isEmpty() ? "" : " (" + tunable.cvar() + ")";
		return tunable.value() + cvar;
	}

	/**
	 * Parses {@code raw} into the field's type and assigns it.
	 *
	 * @return null on success, otherwise a human readable error
	 */
	public String set(String key, String raw) {
		Field field = findField(key);
		if (field == null) {
			return "Unknown setting '" + key + "'";
		}
		Class<?> type = field.getType();
		try {
			Object value;
			if (type == boolean.class) {
				if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
					return key + " expects true or false";
				}
				value = Boolean.parseBoolean(raw);
			} else if (type == int.class) {
				value = Integer.parseInt(raw);
			} else if (type == double.class) {
				value = Double.parseDouble(raw);
			} else if (type == float.class) {
				value = Float.parseFloat(raw);
			} else if (type == BhopMode.class) {
				BhopMode mode = BhopMode.parse(raw);
				if (mode == null) {
					return key + " expects MANUAL or AUTO";
				}
				value = mode;
			} else {
				return "Setting '" + key + "' has an unsupported type";
			}
			Range range = field.getAnnotation(Range.class);
			// Written as "not inside" rather than "outside" so NaN, which parses fine, is refused too.
			if (range != null && value instanceof Number number
					&& !(number.doubleValue() >= range.min() && number.doubleValue() <= range.max())) {
				return field.getName() + " must be between " + trim(range.min()) + " and " + trim(range.max());
			}
			field.set(config, value);
			return null;
		} catch (NumberFormatException e) {
			return "'" + raw + "' is not a valid " + type.getSimpleName().toLowerCase(Locale.ROOT);
		} catch (IllegalAccessException e) {
			return "Could not write '" + key + "'";
		}
	}

	/** Candidate values for completion of {@code /bhop set <key> <value>}. */
	public List<String> valueSuggestions(String key) {
		Field field = findField(key);
		if (field == null) {
			return List.of();
		}
		Class<?> type = field.getType();
		if (type == boolean.class) {
			return List.of("true", "false");
		}
		if (type == BhopMode.class) {
			return List.of("MANUAL", "AUTO");
		}
		try {
			return List.of(String.valueOf(field.get(config)));
		} catch (IllegalAccessException e) {
			return List.of();
		}
	}
}
