package com.minebhop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
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
import java.util.List;
import java.util.Locale;

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
		if (!Files.exists(file)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			BhopConfig loaded = GSON.fromJson(reader, BhopConfig.class);
			if (loaded != null) {
				config = loaded;
			}
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.error("Could not read {}, keeping defaults", file, e);
		}
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
