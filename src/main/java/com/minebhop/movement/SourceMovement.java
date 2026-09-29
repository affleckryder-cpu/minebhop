package com.minebhop.movement;

/**
 * A port of the parts of Source's {@code CGameMovement} that decide how fast you go.
 *
 * <p>These are deliberately plain static functions over {@link SrcVec} with no Minecraft
 * types anywhere: the whole point is that they are the Source formulas verbatim, so they
 * can be read against the engine source and reasoned about on their own.
 *
 * <p>All speeds are Source units per second, all times are seconds.
 */
public final class SourceMovement {
	private SourceMovement() {
	}

	/** Below this, Source treats you as stopped and zeroes out the remainder. */
	private static final double SPEED_EPSILON = 0.1;

	/**
	 * {@code CGameMovement::Friction}.
	 *
	 * <p>Only ever called on a tick where you are on the ground and did not jump. That
	 * condition is the entire mechanic behind bunny hopping: in {@code FullWalkMove} the
	 * jump check runs <i>before</i> friction and clears the ground entity, so a hop timed
	 * onto the landing tick never pays the friction cost and keeps all of its speed.
	 *
	 * @param friction combined {@code sv_friction * surfaceFriction}
	 * @param stopSpeed {@code sv_stopspeed}
	 */
	public static void friction(SrcVec vel, double dt, double friction, double stopSpeed) {
		double speed = vel.horizontalSpeed();
		if (speed < SPEED_EPSILON) {
			return;
		}

		// Under sv_stopspeed the drop is computed as though you were moving at stopspeed,
		// which is what makes slow movement come to a hard stop instead of trailing off.
		double control = Math.max(speed, stopSpeed);
		double drop = control * friction * dt;

		double newSpeed = Math.max(0.0, speed - drop);
		if (newSpeed != speed) {
			vel.scaleHorizontal(newSpeed / speed);
		}
	}

	/**
	 * {@code CGameMovement::Accelerate}, the ground case.
	 *
	 * <p>Acceleration is only ever applied along the wish direction, and only up to the
	 * point where your velocity <i>projected onto that direction</i> reaches wishSpeed.
	 * Speed already carried sideways is therefore never removed by accelerating, it is
	 * only bled off by {@link #friction}.
	 */
	public static void accelerate(SrcVec vel, double wishX, double wishZ, double wishSpeed,
			double accel, double dt, double surfaceFriction) {
		if (wishSpeed <= 0.0) {
			return;
		}

		double currentSpeed = vel.x * wishX + vel.z * wishZ;
		double addSpeed = wishSpeed - currentSpeed;
		if (addSpeed <= 0.0) {
			return;
		}

		double accelSpeed = Math.min(accel * wishSpeed * dt * surfaceFriction, addSpeed);
		vel.x += accelSpeed * wishX;
		vel.z += accelSpeed * wishZ;
	}

	/**
	 * {@code CGameMovement::AirAccelerate}, and the reason air strafing works.
	 *
	 * <p>Two things differ from the ground case, and the interaction between them is the
	 * whole trick:
	 *
	 * <ul>
	 *   <li>The target speed used to compute the headroom is clamped to {@code airSpeedCap}
	 *       (30 u/s), <i>not</i> to wishSpeed. So the projection of your velocity onto the
	 *       wish direction may never exceed 30 u/s.
	 *   <li>The amount actually added still scales with the <i>unclamped</i> wishSpeed, so
	 *       in practice the clamp on {@code addSpeed} is what binds.
	 * </ul>
	 *
	 * <p>Hold a strafe key and turn the mouse the same way, and the wish direction stays
	 * roughly perpendicular to your velocity. The projection onto it stays near zero, the
	 * full 30 u/s of headroom is available every single tick, and every bit of it is added
	 * at a right angle to where you were already going. The magnitude of the sum of two
	 * perpendicular vectors is larger than either, so total speed climbs without bound
	 * while the direction of travel curves round. There is no cap on total velocity here,
	 * only on the component along the input.
	 */
	public static void airAccelerate(SrcVec vel, double wishX, double wishZ, double wishSpeed,
			double airAccel, double airSpeedCap, double dt, double surfaceFriction) {
		if (wishSpeed <= 0.0) {
			return;
		}

		double cappedWishSpeed = Math.min(wishSpeed, airSpeedCap);

		double currentSpeed = vel.x * wishX + vel.z * wishZ;
		double addSpeed = cappedWishSpeed - currentSpeed;
		if (addSpeed <= 0.0) {
			return;
		}

		double accelSpeed = Math.min(airAccel * wishSpeed * dt * surfaceFriction, addSpeed);
		vel.x += accelSpeed * wishX;
		vel.z += accelSpeed * wishZ;
	}

	/** {@code CGameMovement::CheckVelocity}: per-axis clamp to {@code sv_maxvelocity}. */
	public static void clampVelocity(SrcVec vel, double maxVelocity) {
		vel.x = clamp(vel.x, maxVelocity);
		vel.y = clamp(vel.y, maxVelocity);
		vel.z = clamp(vel.z, maxVelocity);
	}

	private static double clamp(double value, double limit) {
		if (Double.isNaN(value)) {
			return 0.0;
		}
		return Math.max(-limit, Math.min(limit, value));
	}

	/**
	 * {@code CGameMovement::PreventBunnyJumping}, applied on jump when
	 * {@code sv_enablebunnyhopping} is off. Clamping here rather than in the air
	 * acceleration is why the official-server behaviour still lets you gain speed
	 * mid-flight but resets you on every landing.
	 */
	public static void preventBunnyJumping(SrcVec vel, double maxSpeed, double capMultiplier) {
		double max = maxSpeed * capMultiplier;
		double speed = vel.horizontalSpeed();
		if (speed > max && speed > 0.0) {
			vel.scaleHorizontal(max / speed);
		}
	}
}
