package com.minebhop.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The allowed range of a numeric {@link BhopConfig} field, inclusive.
 *
 * <p>Enforced in {@link ConfigManager}, so the settings screen's sliders, {@code /bhop set}, custom
 * presets and a hand-edited config file all obey the same limits. They are set wide enough for
 * every built-in preset and for real experimentation, and narrow enough that no value turns the
 * movement into something unrecognisable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {
	double min();

	double max();
}
