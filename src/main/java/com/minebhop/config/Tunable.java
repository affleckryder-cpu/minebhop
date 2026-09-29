package com.minebhop.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link BhopConfig} field as runtime-tunable via {@code /bhop set <key> <value>}.
 *
 * <p>The annotation carries the documentation shown by {@code /bhop list}, so the
 * config schema, the command completions and the help text all stay in one place.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Tunable {
	/** Human readable description, shown by {@code /bhop get} and {@code /bhop list}. */
	String value();

	/** Unit suffix for display purposes, e.g. {@code "u/s"}. Empty for unitless values. */
	String unit() default "";

	/** The equivalent Source engine cvar, if there is one. Purely informational. */
	String cvar() default "";
}
