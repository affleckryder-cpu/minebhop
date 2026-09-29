package com.minebhop.movement;

import com.minebhop.MineBhop;
import com.minebhop.config.BhopConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The bridge between Minecraft's entity movement and {@link SourceMovement}.
 *
 * <p>Called from a HEAD injection on {@code Player#travel}, which it cancels. It converts
 * Minecraft's blocks-per-tick velocity into Source units per second, runs the Source
 * movement pipeline, hands the result back to Minecraft's collision code and converts
 * back. Minecraft still owns collision, step-up and block speed factors; only the velocity
 * integration is replaced.
 *
 * <p>Tick order follows Source's {@code FullWalkMove}:
 * <pre>
 *   StartGravity -> CheckJumpButton -> Friction -> Accelerate -> Move -> FinishGravity
 * </pre>
 * Jump before friction is the load-bearing detail; see {@link SourceMovement#friction}.
 */
public final class SourceMoveHandler {
	private SourceMoveHandler() {
	}

	/** Minecraft's fixed timestep. */
	private static final double TICK_SECONDS = 0.05;

	private static float prevYaw;
	private static boolean hasPrevYaw;
	private static boolean serverNoticeShown;

	/** Called between worlds, so each session starts with fresh per-world state. */
	public static void resetForNewWorld() {
		hasPrevYaw = false;
		serverNoticeShown = false;
	}

	/**
	 * Whether Source movement may run in this session at all: always in singleplayer and for a LAN
	 * host (both run an integrated server in this JVM), and on any other server only once the player
	 * opts in -- to a server's anti-cheat this movement is indistinguishable from a speed hack.
	 */
	public static boolean allowedHere() {
		return Minecraft.getInstance().hasSingleplayerServer() || MineBhop.config().allowOnServers;
	}

	/**
	 * Whether the Source simulation should take over this tick.
	 *
	 * <p>Everything Minecraft-specific -- swimming, elytra, ladders, creative flight,
	 * riding -- is deliberately left to vanilla, both because Source has no equivalent
	 * and because replacing them would break traversal in ways that have nothing to do
	 * with bunny hopping.
	 */
	public static boolean shouldHandle(LocalPlayer player) {
		BhopConfig config = MineBhop.config();
		if (!config.enabled) {
			return false;
		}
		if (player != Minecraft.getInstance().player) {
			return false;
		}
		if (!allowedHere()) {
			if (!serverNoticeShown) {
				serverNoticeShown = true;
				player.sendSystemMessage(Component.literal("[MineBhop] Source movement is off on multiplayer "
						+ "servers, where anti-cheat sees it as a speed hack. If this server allows it: "
						+ "/bhop set allowOnServers true"));
			}
			return false;
		}
		if (player.onClimbable() && !config.sourceLadders) {
			// Ladders are handled by travel() below when sourceLadders is on; otherwise hand the
			// whole tick back to vanilla climbing.
			return false;
		}

		return !player.isPassenger()
				&& !player.getAbilities().flying
				&& !player.isSpectator()
				&& !player.isFallFlying()
				&& !player.isSwimming()
				&& !player.isInWater()
				&& !player.isInLava();
	}

	/** Runs one tick of Source movement and moves the player. */
	public static void travel(LocalPlayer player) {
		BhopConfig config = MineBhop.config();
		MovementState state = MineBhop.state();

		// (u/s) per (blocks/tick). One block is one metre; one Source unit is one inch.
		final double scale = config.unitsPerBlock * 20.0;

		// Ladders replace the whole pipeline: Source sets velocity outright while climbing, with
		// no acceleration, no friction and no gravity.
		boolean onLadderCooldown = state.tickLadderCooldown();
		if (config.sourceLadders && player.onClimbable() && !onLadderCooldown) {
			ladderMove(player, config, state, scale);
			return;
		}

		Vec3 initial = player.getDeltaMovement();
		SrcVec velocity = new SrcVec(initial.x * scale, initial.y * scale, initial.z * scale);

		boolean grounded = player.onGround();

		Input keys = player.input.keyPresses;
		double forwardMove = (keys.forward() ? 1.0 : 0.0) - (keys.backward() ? 1.0 : 0.0);
		double sideMove = (keys.right() ? 1.0 : 0.0) - (keys.left() ? 1.0 : 0.0);
		boolean hasInput = forwardMove != 0.0 || sideMove != 0.0;

		double maxSpeed = config.maxSpeed;
		if (keys.shift()) {
			maxSpeed *= config.duckSpeedMultiplier;
		}
		double wishSpeed = hasInput ? maxSpeed : 0.0;

		// --- CheckJumpButton, ahead of friction so a perfect hop keeps its speed ---
		boolean jumped = state.decideJump(keys.jump(), grounded, config);
		if (jumped) {
			if (!config.enableBunnyHopping) {
				SourceMovement.preventBunnyJumping(velocity, config.maxSpeed, config.bhopSpeedCap);
			}
			velocity.y = config.jumpImpulse;
			// Source clears the ground entity here, so this tick runs as an air tick.
			grounded = false;
		}
		state.breakStreakOnLanding(grounded, config);

		int subSteps = Math.max(1, Math.round(config.simulationTickrate / 20.0f));
		double dt = TICK_SECONDS / subSteps;

		float currentYaw = player.getYRot();
		if (!hasPrevYaw) {
			prevYaw = currentYaw;
			hasPrevYaw = true;
		}
		// Air strafing gains speed from turning while accelerating. Minecraft samples the
		// mouse once per 20 Hz tick, so without walking the yaw across the sub-steps the
		// simulation would accelerate against a stale direction and lose most of the gain.
		float yawDelta = config.interpolateStrafeYaw ? (float) Mth.wrapDegrees(currentYaw - prevYaw) : 0.0f;

		if (grounded) {
			// Source zeroes vertical velocity on the ground. Minecraft instead infers ground
			// contact from downward motion during move(), so keep a token downward speed.
			velocity.y = -config.groundStickSpeed;

			double frictionAmount = config.friction * config.surfaceFriction;
			for (int step = 0; step < subSteps; step++) {
				double yaw = yawFor(currentYaw, yawDelta, subSteps, step);
				double[] wish = wishDirection(yaw, forwardMove, sideMove);
				SourceMovement.friction(velocity, dt, frictionAmount, config.stopSpeed);
				SourceMovement.accelerate(velocity, wish[0], wish[1], wishSpeed,
						config.acceleration, dt, config.surfaceFriction);
			}
		} else {
			// StartGravity: half the tick's gravity before the move.
			velocity.y -= config.gravity * TICK_SECONDS * 0.5;

			for (int step = 0; step < subSteps; step++) {
				double yaw = yawFor(currentYaw, yawDelta, subSteps, step);
				double[] wish = wishDirection(yaw, forwardMove, sideMove);
				SourceMovement.airAccelerate(velocity, wish[0], wish[1], wishSpeed,
						config.airAcceleration, config.airSpeedCap, dt, config.surfaceFriction);
			}
		}

		SourceMovement.clampVelocity(velocity, config.maxVelocity);

		// Hand off to Minecraft for collision, step-up and block speed factors.
		player.setDeltaMovement(velocity.x / scale, velocity.y / scale, velocity.z / scale);
		player.move(MoverType.SELF, player.getDeltaMovement());

		// Read back: move() zeroes axes that collided and may have scaled us.
		Vec3 moved = player.getDeltaMovement();
		velocity.set(moved.x * scale, moved.y * scale, moved.z * scale);

		if (!grounded) {
			// FinishGravity: the other half, after the move.
			velocity.y -= config.gravity * TICK_SECONDS * 0.5;
		}

		SourceMovement.clampVelocity(velocity, config.maxVelocity);
		player.setDeltaMovement(velocity.x / scale, velocity.y / scale, velocity.z / scale);

		state.recordSpeed(velocity.horizontalSpeed());
		prevYaw = currentYaw;
	}

	/**
	 * Source's {@code LadderMove} / {@code FullLadderMove}.
	 *
	 * <p>The defining difference from Minecraft is that you climb along your <b>look vector</b>,
	 * not simply upward. Forward input is projected onto the full look direction including pitch,
	 * so looking up climbs, looking down descends, and looking level at the ladder holds you still
	 * because all of your input points into the ladder surface. Strafe input slides you sideways
	 * across the face.
	 *
	 * <p>Velocity is assigned outright rather than accelerated toward, and no gravity is applied,
	 * which is exactly what Source does while attached. Jumping launches you along the ladder's
	 * normal with no upward component, so you drop away as you leave.
	 */
	private static void ladderMove(LocalPlayer player, BhopConfig config, MovementState state, double scale) {
		Input keys = player.input.keyPresses;
		Vec3 normal = ladderNormal(player);

		if (keys.jump()) {
			// Push away from the ladder and refuse to re-grab for a few ticks, since the player is
			// still standing inside the ladder's own block space.
			Vec3 away = normal != null ? normal : horizontalFacing(player.getYRot()).scale(-1.0);
			double speed = config.ladderDismountSpeed / scale;
			player.setDeltaMovement(away.x * speed, 0.0, away.z * speed);
			state.startLadderCooldown(config.ladderGrabCooldownTicks);
			player.move(MoverType.SELF, player.getDeltaMovement());
			state.recordSpeed(horizontalSpeedOf(player, scale));
			return;
		}

		double forwardMove = (keys.forward() ? 1.0 : 0.0) - (keys.backward() ? 1.0 : 0.0);
		double sideMove = (keys.right() ? 1.0 : 0.0) - (keys.left() ? 1.0 : 0.0);

		Vec3 velocity = Vec3.ZERO;
		if (forwardMove != 0.0 || sideMove != 0.0) {
			double yaw = Math.toRadians(player.getYRot());
			Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
			double climbSpeed = config.ladderClimbSpeed / scale;

			// The diagonal climb. A strafe vector is horizontal, so on its own it can never add
			// vertical speed -- holding two perpendicular keys has to raise the climb speed
			// explicitly for the technique to be worth anything.
			if (forwardMove != 0.0 && sideMove != 0.0) {
				climbSpeed *= config.ladderDiagonalBoost;
			}

			// Scale to full climb speed first, then project. Doing it in this order -- rather than
			// projecting and renormalising -- is what makes a shallow look angle climb slowly and a
			// steep one climb fast, because the projection is what removes the speed.
			Vec3 wish = player.getLookAngle().scale(forwardMove).add(right.scale(sideMove)).scale(climbSpeed);

			// You cannot move into the ladder, so drop that component -- but only that one. Motion
			// away from the ladder is how you step off it, and zeroing it too would trap you
			// against the wall with no way out but jumping.
			if (normal != null) {
				double intoLadder = wish.dot(normal);
				if (intoLadder < 0.0) {
					wish = wish.subtract(normal.scale(intoLadder));
				}
			}

			// Deliberately no clamp back down to climb speed here. Clamping is what makes a
			// diagonal slower than plain forward: the combined vector is sqrt(2) longer, so
			// rescaling it to climb speed shrinks the vertical part. The input geometry already
			// bounds the result at sqrt(2) * climbSpeed.
			velocity = wish;
		}

		player.setDeltaMovement(velocity);
		player.move(MoverType.SELF, player.getDeltaMovement());
		state.recordSpeed(horizontalSpeedOf(player, scale));
	}

	/**
	 * Unit vector pointing out of the ladder's face, or null if the block has no facing (vines and
	 * scaffolding, which are climbable from any side). Minecraft's {@code FACING} on a ladder points
	 * away from the wall it is mounted on, which is the same convention as Source's ladder normal.
	 */
	private static Vec3 ladderNormal(LocalPlayer player) {
		BlockState state = player.getInBlockState();
		if (state.hasProperty(LadderBlock.FACING)) {
			return state.getValue(LadderBlock.FACING).getUnitVec3();
		}
		return null;
	}

	/** Horizontal unit vector the player faces, ignoring pitch. */
	private static Vec3 horizontalFacing(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static double horizontalSpeedOf(LocalPlayer player, double scale) {
		Vec3 movement = player.getDeltaMovement();
		return Math.sqrt(movement.x * movement.x + movement.z * movement.z) * scale;
	}

	/**
	 * Yaw at the end of sub-step {@code step}, walking from last tick's yaw to this one's.
	 * Returns radians.
	 */
	private static double yawFor(float currentYaw, float yawDelta, int subSteps, int step) {
		double progress = (step + 1.0) / subSteps;
		return Math.toRadians(currentYaw - yawDelta * (1.0 - progress));
	}

	/**
	 * Normalised wish direction in world space as {@code {x, z}}.
	 *
	 * <p>Built from the raw key states rather than the {@code Vec3} vanilla passes to
	 * {@code travel}, because that one has already had Minecraft's sneak slowdown, the 0.98
	 * scaling and the square-movement normalisation folded into it. Source wants a clean
	 * unit vector with its own speed applied on top.
	 */
	private static double[] wishDirection(double yawRadians, double forwardMove, double sideMove) {
		double sin = Math.sin(yawRadians);
		double cos = Math.cos(yawRadians);

		// Minecraft yaw 0 faces +Z. Forward is (-sin, cos); right is (-cos, -sin).
		double x = -sin * forwardMove - cos * sideMove;
		double z = cos * forwardMove - sin * sideMove;

		double length = Math.sqrt(x * x + z * z);
		if (length < 1.0e-6) {
			return new double[] { 0.0, 0.0 };
		}
		return new double[] { x / length, z / length };
	}
}
