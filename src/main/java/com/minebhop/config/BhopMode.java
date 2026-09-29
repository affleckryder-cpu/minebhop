package com.minebhop.config;

/** How the jump button is translated into hops. */
public enum BhopMode {
	/**
	 * One hop per key press. Combined with {@code jumpBufferTicks} this is the
	 * classic "you have to time it yourself" bunny hop.
	 */
	MANUAL,
	/**
	 * Holding the jump key hops on every landing, the equivalent of
	 * {@code sv_autobunnyhopping 1}.
	 */
	AUTO;

	public static BhopMode parse(String raw) {
		for (BhopMode mode : values()) {
			if (mode.name().equalsIgnoreCase(raw)) {
				return mode;
			}
		}
		return null;
	}
}
