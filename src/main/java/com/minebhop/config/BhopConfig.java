package com.minebhop.config;

/**
 * Every tunable value of the movement simulation.
 *
 * <p>Speeds and accelerations are expressed in <b>Source engine units</b> (1 unit = 1 inch)
 * so that the defaults are literally the CS:GO cvar values and can be compared against a
 * CS config one-to-one. Conversion into Minecraft's blocks-per-tick happens in exactly one
 * place, {@link com.minebhop.movement.SourceMoveHandler}.
 */
public class BhopConfig {

	// ------------------------------------------------------------------
	// Master switches
	// ------------------------------------------------------------------

	@Tunable("Master switch for the Source movement simulation.")
	public boolean enabled = true;

	@Tunable("Allow Source movement on multiplayer servers. Off by default: to a server's anti-cheat this "
			+ "movement is indistinguishable from a speed hack, and players get kicked or banned for it. Turn "
			+ "it on only for servers that permit it. Singleplayer and hosting a LAN world always work.")
	public boolean allowOnServers = false;

	@Tunable("Show the speedometer / hop timing HUD. Off by default -- it is a practice aid, not "
			+ "something you want cluttering the screen while playing. Toggle with the '[' key.")
	public boolean hud = false;

	@Tunable("Show your speed on the XP bar while moving: the bar fills toward 1000 u/s and runs green to "
			+ "yellow to red, and the level number shows u/s. Standing still, your normal XP shows. Works "
			+ "alongside or instead of the speedometer (hud).")
	public boolean xpBarSpeed = false;

	@Tunable("Print KZ-style stats in chat after every jump: distance (edge to edge, like KZ), speed at "
			+ "takeoff, top speed in the air, strafe count and sync -- the share of mouse turns that went "
			+ "toward the strafe key you held, averaged over the hop chain. With the HUD on, the stats show "
			+ "there instead of in chat. Off by default, since bhopping prints a line per hop.")
	public boolean jumpStats = false;

	@Tunable("Suppress fall damage entirely. Off by default -- fall damage is part of the game, and "
			+ "preserveMomentumOnDamage already stops it from costing you speed, which is the part "
			+ "that actually ruins a hop chain. Only works in singleplayer and for a LAN host, since "
			+ "damage is decided by the authoritative side.")
	public boolean disableFallDamage = false;

	@Tunable("Ignore server velocity corrections that would bleed off horizontal speed.\n"
			+ "Taking damage makes the server call markHurt(), which pushes a "
			+ "ClientboundSetEntityMotionPacket back at you carrying the server's own idea of your "
			+ "velocity. The server never simulates Source movement, so that value is nothing like "
			+ "your real speed and applying it wipes out a hop chain -- most visibly on fall damage. "
			+ "Corrections that would speed you up are still applied, so explosions and mob knockback "
			+ "keep working.")
	public boolean preserveMomentumOnDamage = true;

	// ------------------------------------------------------------------
	// Core Source movement cvars
	// ------------------------------------------------------------------

	@Tunable(value = "Ground running speed.", unit = "u/s", cvar = "sv_maxspeed")
	public double maxSpeed = 250.0;

	@Tunable(value = "Ground acceleration factor.", cvar = "sv_accelerate")
	public double acceleration = 5.5;

	@Tunable(value = "Air acceleration factor. This is what makes strafing gain speed.", cvar = "sv_airaccelerate")
	public double airAcceleration = 12.0;

	@Tunable(value = "Ground friction. Applied only on ticks where you are grounded and did not jump.", cvar = "sv_friction")
	public double friction = 5.2;

	@Tunable(value = "Below this speed, friction is applied as if you were moving at this speed. "
			+ "Produces the sharp stop at low speed.", unit = "u/s", cvar = "sv_stopspeed")
	public double stopSpeed = 80.0;

	@Tunable(value = "The air speed cap. Per tick, air acceleration can only add speed until your "
			+ "velocity projected onto the wish direction reaches this. The single most important "
			+ "number in Source movement -- raising it makes strafing gain speed absurdly fast.", unit = "u/s")
	public double airSpeedCap = 30.0;

	@Tunable(value = "Gravity.", unit = "u/s^2", cvar = "sv_gravity")
	public double gravity = 800.0;

	@Tunable(value = "Upward velocity applied on jump. 301.993 = sqrt(2 * 800 * 57), a 57 unit jump.",
			unit = "u/s")
	public double jumpImpulse = 301.993;

	@Tunable(value = "Hard clamp on any single velocity axis.", unit = "u/s", cvar = "sv_maxvelocity")
	public double maxVelocity = 3500.0;

	@Tunable(value = "Surface friction multiplier, scales both friction and acceleration.")
	public double surfaceFriction = 1.0;

	@Tunable("Speed multiplier while crouching (sneak key).")
	public double duckSpeedMultiplier = 0.34;

	// ------------------------------------------------------------------
	// Bunny hop rules
	// ------------------------------------------------------------------

	@Tunable("MANUAL = one hop per jump key press. AUTO = hold jump to hop on every landing.")
	public BhopMode bhopMode = BhopMode.AUTO;

	@Tunable("False reproduces sv_enablebunnyhopping 0: horizontal speed is clamped on every jump, "
			+ "which is what stops bhopping on official CS:GO servers. True lets you keep accelerating.")
	public boolean enableBunnyHopping = true;

	@Tunable("Speed clamp on jump as a multiple of maxSpeed, used only when enableBunnyHopping is false.")
	public double bhopSpeedCap = 1.2;

	// ------------------------------------------------------------------
	// Hop timing
	// ------------------------------------------------------------------

	@Tunable("Ticks you must already have been grounded before a hop fires. 0 hops on the very tick "
			+ "you land, which skips ground friction entirely and preserves all your speed.")
	public int autoHopDelayTicks = 0;

	@Tunable("Input buffer. A jump pressed this many ticks before touching the ground still hops on "
			+ "landing, instead of being eaten. MANUAL mode only.")
	public int jumpBufferTicks = 3;

	@Tunable("A hop leaving the ground within this many ticks of landing counts as perfect and is "
			+ "reported as such on the HUD. Purely feedback -- speed retention is decided by physics.")
	public int perfectHopWindowTicks = 1;

	@Tunable("Minimum ticks between two hops. 0 for no cooldown.")
	public int minTicksBetweenHops = 0;

	// ------------------------------------------------------------------
	// Ladders
	// ------------------------------------------------------------------

	@Tunable("Use Source ladder movement instead of Minecraft's. You climb along the direction you "
			+ "are looking rather than simply upward: look up to go up, look down to go down, and "
			+ "strafe to slide sideways across the ladder. Looking level at the ladder holds you in "
			+ "place, because all of your input is pointing into the ladder surface. Off falls back "
			+ "to vanilla climbing.")
	public boolean sourceLadders = true;

	@Tunable(value = "Ladder climb speed. Applied directly with no acceleration, the way Source does "
			+ "it. Minecraft's own climb is about 157 u/s for comparison.", unit = "u/s")
	public double ladderClimbSpeed = 200.0;

	@Tunable(value = "Speed of the push away from a ladder when you jump off it. Directed along the "
			+ "ladder's facing, with no upward component, so you drop as you leave -- as in Source.",
			unit = "u/s")
	public double ladderDismountSpeed = 270.0;

	@Tunable("Ticks after jumping off a ladder during which you cannot re-grab it. Without this you "
			+ "would immediately reattach, since you are still inside the ladder's block.")
	public int ladderGrabCooldownTicks = 8;

	@Tunable("Climb speed multiplier while holding a forward/back key AND a strafe key together -- "
			+ "the diagonal ladder climb. sqrt(2) = 1.414 matches the length of a diagonal input "
			+ "vector, so a diagonal climbs about 41% faster than forward alone. Set to 1.0 to "
			+ "remove the technique; a strafe key then just slides you sideways as normal.")
	public double ladderDiagonalBoost = 1.414;

	// ------------------------------------------------------------------
	// Simulation fidelity
	// ------------------------------------------------------------------

	@Tunable("Tickrate the air acceleration is integrated at. Minecraft runs at 20 Hz, CS:GO at 64. "
			+ "Because the air speed cap is applied per step, a 20 Hz simulation gains speed roughly "
			+ "3x slower than CS:GO. This sub-steps acceleration to restore the real feel.")
	public int simulationTickrate = 64;

	@Tunable("Interpolate view yaw across the sub-steps of a tick. Air strafing gains speed from "
			+ "turning while accelerating, so without this a 20 Hz sample of your mouse movement "
			+ "loses most of the gain.")
	public boolean interpolateStrafeYaw = true;

	@Tunable("Units per block. 39.3701 maps one Source unit to one inch, which puts CS:GO's 250 u/s "
			+ "run at 0.318 blocks/tick -- just above a Minecraft sprint. Lower it to scale the whole "
			+ "movement model up relative to the world.")
	public double unitsPerBlock = 39.3701;

	@Tunable(value = "Downward velocity applied while grounded so Minecraft's collision keeps "
			+ "reporting ground contact. Has no effect on horizontal physics.", unit = "u/s")
	public double groundStickSpeed = 20.0;

	// ------------------------------------------------------------------
	// Presets
	// ------------------------------------------------------------------

	/** Applies a named preset in place. Returns false if the name is unknown. */
	public boolean applyPreset(String name) {
		switch (name.toLowerCase()) {
			case "csgo" -> {
				maxSpeed = 250.0;
				acceleration = 5.5;
				airAcceleration = 12.0;
				friction = 5.2;
				stopSpeed = 80.0;
				airSpeedCap = 30.0;
				gravity = 800.0;
				jumpImpulse = 301.993;
				enableBunnyHopping = true;
				simulationTickrate = 64;
			}
			case "css" -> {
				// Counter-Strike: Source
				maxSpeed = 250.0;
				acceleration = 5.0;
				airAcceleration = 10.0;
				friction = 4.0;
				stopSpeed = 75.0;
				airSpeedCap = 30.0;
				gravity = 800.0;
				jumpImpulse = 301.993;
				enableBunnyHopping = true;
				simulationTickrate = 66;
			}
			case "kz" -> {
				// Climb / bhop server settings: very high air accel, 128 tick.
				maxSpeed = 250.0;
				acceleration = 5.5;
				airAcceleration = 100.0;
				friction = 5.2;
				stopSpeed = 80.0;
				airSpeedCap = 30.0;
				gravity = 800.0;
				jumpImpulse = 301.993;
				enableBunnyHopping = true;
				simulationTickrate = 128;
			}
			case "quake" -> {
				// Uncapped air control, the CPMA-style feel Source inherited its formula from.
				maxSpeed = 320.0;
				acceleration = 10.0;
				airAcceleration = 1.0;
				friction = 6.0;
				stopSpeed = 100.0;
				airSpeedCap = 320.0;
				gravity = 800.0;
				jumpImpulse = 270.0;
				enableBunnyHopping = true;
				simulationTickrate = 125;
			}
			case "official" -> {
				// Matchmaking CS:GO, where bunny hopping is deliberately broken.
				applyPreset("csgo");
				enableBunnyHopping = false;
				bhopSpeedCap = 1.2;
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	public static String[] presetNames() {
		return new String[] { "csgo", "css", "kz", "quake", "official" };
	}
}
