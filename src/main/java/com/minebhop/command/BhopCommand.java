package com.minebhop.command;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
import com.minebhop.config.BhopMode;
import com.minebhop.config.ConfigManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.Locale;

/**
 * The {@code /bhop} client command tree.
 *
 * <p>Generic over the command source and built from raw Brigadier rather than either loader's
 * helper class, because Fabric hands us a {@code FabricClientCommandSource} and NeoForge a
 * {@code CommandSourceStack}. Each loader supplies a {@link Feedback} to bridge the one thing that
 * genuinely differs: how a line of text reaches the player.
 */
public final class BhopCommand {
	private BhopCommand() {
	}

	/** How a message reaches the player on a given loader. */
	public interface Feedback<S> {
		void send(S source, String message);

		void error(S source, String message);
	}

	private static <S> SuggestionProvider<S> keys() {
		return (context, builder) -> {
			String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
			for (String key : ConfigManager.keys()) {
				if (key.toLowerCase(Locale.ROOT).startsWith(remaining)) {
					builder.suggest(key);
				}
			}
			return builder.buildFuture();
		};
	}

	private static <S> SuggestionProvider<S> values() {
		return (context, builder) -> {
			String key = StringArgumentType.getString(context, "key");
			String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
			for (String value : MineBhop.configManager().valueSuggestions(key)) {
				if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
					builder.suggest(value);
				}
			}
			return builder.buildFuture();
		};
	}

	private static <S> SuggestionProvider<S> presets() {
		return (context, builder) -> {
			String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
			for (String preset : BhopConfig.presetNames()) {
				if (preset.startsWith(remaining)) {
					builder.suggest(preset);
				}
			}
			return builder.buildFuture();
		};
	}

	private static <S> LiteralArgumentBuilder<S> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <S> RequiredArgumentBuilder<S, String> word(String name) {
		return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
	}

	public static <S> void register(CommandDispatcher<S> dispatcher, Feedback<S> feedback) {
		dispatcher.register(BhopCommand.<S>literal("bhop")
				.executes(context -> status(context, feedback))

				.then(BhopCommand.<S>literal("on").executes(context -> setEnabled(context, feedback, true)))
				.then(BhopCommand.<S>literal("off").executes(context -> setEnabled(context, feedback, false)))

				.then(BhopCommand.<S>literal("hud").executes(context -> {
					BhopConfig config = MineBhop.config();
					config.hud = !config.hud;
					return saveAndReport(context, feedback, "HUD " + (config.hud ? "shown" : "hidden"));
				}))

				.then(BhopCommand.<S>literal("mode")
						.then(BhopCommand.<S>word("mode")
								.suggests((context, builder) -> {
									builder.suggest("MANUAL");
									builder.suggest("AUTO");
									return builder.buildFuture();
								})
								.executes(context -> {
									BhopMode mode = BhopMode.parse(StringArgumentType.getString(context, "mode"));
									if (mode == null) {
										return error(context, feedback, "Expected MANUAL or AUTO");
									}
									MineBhop.config().bhopMode = mode;
									return saveAndReport(context, feedback, "Hop mode set to " + mode);
								})))

				.then(BhopCommand.<S>literal("preset")
						.then(BhopCommand.<S>word("name")
								.suggests(presets())
								.executes(context -> {
									String name = StringArgumentType.getString(context, "name");
									if (!MineBhop.config().applyPreset(name)) {
										return error(context, feedback, "Unknown preset. Try one of: "
												+ String.join(", ", BhopConfig.presetNames()));
									}
									MineBhop.state().reset();
									return saveAndReport(context, feedback,
											"Applied preset " + name.toLowerCase(Locale.ROOT));
								})))

				.then(BhopCommand.<S>literal("set")
						.then(BhopCommand.<S>word("key")
								.suggests(keys())
								.then(RequiredArgumentBuilder.<S, String>argument("value", StringArgumentType.greedyString())
										.suggests(values())
										.executes(context -> {
											String key = StringArgumentType.getString(context, "key");
											String value = StringArgumentType.getString(context, "value").trim();
											String failure = MineBhop.configManager().set(key, value);
											if (failure != null) {
												return error(context, feedback, failure);
											}
											return saveAndReport(context, feedback,
													MineBhop.configManager().describeValue(key));
										}))))

				.then(BhopCommand.<S>literal("get")
						.then(BhopCommand.<S>word("key")
								.suggests(keys())
								.executes(context -> {
									String key = StringArgumentType.getString(context, "key");
									String value = MineBhop.configManager().describeValue(key);
									if (value == null) {
										return error(context, feedback, "Unknown setting: " + key);
									}
									feedback.send(context.getSource(), value);
									feedback.send(context.getSource(), "  " + ConfigManager.describeKey(key));
									return 1;
								})))

				.then(BhopCommand.<S>literal("menu").executes(context -> {
					MineBhop.requestMenu();
					return 1;
				}))

				.then(BhopCommand.<S>literal("list").executes(context -> {
					feedback.send(context.getSource(), "--- MineBhop settings ---");
					for (String key : ConfigManager.keys()) {
						feedback.send(context.getSource(), MineBhop.configManager().describeValue(key));
					}
					feedback.send(context.getSource(), "Use /bhop get <key> for what a setting does.");
					return 1;
				}))

				.then(BhopCommand.<S>literal("reset").executes(context -> {
					MineBhop.configManager().reset();
					MineBhop.state().reset();
					return saveAndReport(context, feedback, "Config reset to CS:GO defaults");
				}))

				.then(BhopCommand.<S>literal("reload").executes(context -> {
					MineBhop.configManager().load();
					MineBhop.state().reset();
					feedback.send(context.getSource(), "Reloaded " + MineBhop.configManager().file());
					return 1;
				}))

				.then(BhopCommand.<S>literal("save").executes(context -> {
					MineBhop.configManager().save();
					feedback.send(context.getSource(), "Saved " + MineBhop.configManager().file());
					return 1;
				})));
	}

	private static <S> int status(CommandContext<S> context, Feedback<S> feedback) {
		BhopConfig config = MineBhop.config();
		S source = context.getSource();
		feedback.send(source, "--- MineBhop ---");
		feedback.send(source, "Source movement: " + (config.enabled ? "on" : "off"));
		feedback.send(source, "Hop mode: " + config.bhopMode
				+ "  (buffer " + config.jumpBufferTicks + "t, delay " + config.autoHopDelayTicks
				+ "t, perfect window " + config.perfectHopWindowTicks + "t)");
		feedback.send(source, "Speed " + fmt(config.maxSpeed) + " u/s, air accel " + fmt(config.airAcceleration)
				+ ", air cap " + fmt(config.airSpeedCap) + " u/s, friction " + fmt(config.friction));
		feedback.send(source, "Bunny hopping: " + (config.enableBunnyHopping
				? "uncapped"
				: "capped at " + fmt(config.bhopSpeedCap * config.maxSpeed) + " u/s on jump"));
		feedback.send(source, "Ladders: " + (config.sourceLadders
				? fmt(config.ladderClimbSpeed) + " u/s, diagonal x" + fmt(config.ladderDiagonalBoost)
				: "vanilla"));
		feedback.send(source, "Simulating at " + config.simulationTickrate + " Hz");
		feedback.send(source, "/bhop list shows every setting, /bhop preset <name> loads a preset.");
		return 1;
	}

	private static <S> int setEnabled(CommandContext<S> context, Feedback<S> feedback, boolean enabled) {
		MineBhop.config().enabled = enabled;
		MineBhop.state().reset();
		return saveAndReport(context, feedback, "Source movement " + (enabled ? "enabled" : "disabled"));
	}

	private static <S> int saveAndReport(CommandContext<S> context, Feedback<S> feedback, String message) {
		MineBhop.configManager().save();
		feedback.send(context.getSource(), message);
		return 1;
	}

	private static <S> int error(CommandContext<S> context, Feedback<S> feedback, String message) {
		feedback.error(context.getSource(), message);
		return 0;
	}

	private static String fmt(double value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(Locale.ROOT, "%.2f", value);
	}
}
