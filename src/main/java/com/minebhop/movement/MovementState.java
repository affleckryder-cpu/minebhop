package com.minebhop.movement;

import com.minebhop.config.BhopConfig;
import com.minebhop.config.BhopMode;

/**
 * Per-player transient movement state: the hop timing state machine plus the statistics
 * the HUD reads.
 *
 * <p>Nothing in here is persisted. It is reset whenever the player respawns or changes
 * world, via {@link #reset()}.
 */
public final class MovementState {

	// --- hop timing ---
	private boolean prevJumpHeld;
	private int jumpBufferRemaining;
	private int groundTicks;
	private int hopCooldown;

	// --- ladders ---
	private int ladderCooldown;

	// --- statistics for the HUD ---
	private int hopStreak;
	private int lastHopGroundTicks;
	private boolean lastHopPerfect;
	private double speedAtLastHop;
	private double speedDelta;
	private double topSpeed;
	private boolean jumpedThisTick;

	public void reset() {
		prevJumpHeld = false;
		jumpBufferRemaining = 0;
		groundTicks = 0;
		hopCooldown = 0;
		ladderCooldown = 0;
		hopStreak = 0;
		lastHopGroundTicks = 0;
		lastHopPerfect = false;
		speedAtLastHop = 0.0;
		speedDelta = 0.0;
		topSpeed = 0.0;
		jumpedThisTick = false;
	}

	/**
	 * Decides whether a hop fires this tick, mirroring the order in {@code FullWalkMove}
	 * where {@code CheckJumpButton} runs ahead of {@code Friction}.
	 *
	 * @param jumpHeld whether the jump key is down right now
	 * @param grounded whether Minecraft currently reports ground contact
	 * @return true if the caller should apply the jump impulse and treat this tick as airborne
	 */
	public boolean decideJump(boolean jumpHeld, boolean grounded, BhopConfig config) {
		boolean pressedThisTick = jumpHeld && !prevJumpHeld;
		prevJumpHeld = jumpHeld;

		// A press shortly before touchdown stays live for jumpBufferTicks, so a hop entered
		// slightly early lands on the ground tick instead of being swallowed.
		if (pressedThisTick) {
			jumpBufferRemaining = Math.max(0, config.jumpBufferTicks) + 1;
		} else if (jumpBufferRemaining > 0) {
			jumpBufferRemaining--;
		}

		if (hopCooldown > 0) {
			hopCooldown--;
		}

		if (grounded) {
			groundTicks++;
		} else {
			groundTicks = 0;
		}

		boolean wantsHop = config.bhopMode == BhopMode.AUTO
				? jumpHeld
				: jumpBufferRemaining > 0;

		boolean canHop = grounded
				&& groundTicks > Math.max(0, config.autoHopDelayTicks)
				&& hopCooldown == 0;

		jumpedThisTick = wantsHop && canHop;

		if (jumpedThisTick) {
			// Ticks spent grounded before leaving again. Zero is frame perfect: the hop
			// fires on the same tick as the landing, so friction never runs.
			lastHopGroundTicks = groundTicks - 1;
			lastHopPerfect = lastHopGroundTicks <= Math.max(0, config.perfectHopWindowTicks);
			hopStreak = lastHopPerfect ? hopStreak + 1 : 0;

			jumpBufferRemaining = 0;
			hopCooldown = Math.max(0, config.minTicksBetweenHops);
			groundTicks = 0;
		}

		return jumpedThisTick;
	}

	/**
	 * Whether the player is still in the no-regrab window after jumping off a ladder. Ticks the
	 * timer down as a side effect, so call it exactly once per tick.
	 */
	public boolean tickLadderCooldown() {
		if (ladderCooldown > 0) {
			ladderCooldown--;
			return true;
		}
		return false;
	}

	public void startLadderCooldown(int ticks) {
		ladderCooldown = Math.max(0, ticks);
	}

	/** Records end-of-tick speed statistics. */
	public void recordSpeed(double horizontalSpeed) {
		if (jumpedThisTick) {
			speedDelta = horizontalSpeed - speedAtLastHop;
			speedAtLastHop = horizontalSpeed;
		}
		topSpeed = Math.max(topSpeed, horizontalSpeed);
	}

	/**
	 * Ends the streak once the player has sat on the ground past the perfect-hop window,
	 * so the HUD stops claiming a chain that has already been broken.
	 */
	public void breakStreakOnLanding(boolean grounded, BhopConfig config) {
		if (grounded && !jumpedThisTick && groundTicks > Math.max(0, config.perfectHopWindowTicks) + 1) {
			hopStreak = 0;
		}
	}

	public int hopStreak() {
		return hopStreak;
	}

	public int lastHopGroundTicks() {
		return lastHopGroundTicks;
	}

	public boolean lastHopPerfect() {
		return lastHopPerfect;
	}

	/** Speed gained or lost across the most recent hop, in u/s. */
	public double speedDelta() {
		return speedDelta;
	}

	public double topSpeed() {
		return topSpeed;
	}

	public void resetTopSpeed() {
		topSpeed = 0.0;
	}

	public boolean jumpedThisTick() {
		return jumpedThisTick;
	}
}
