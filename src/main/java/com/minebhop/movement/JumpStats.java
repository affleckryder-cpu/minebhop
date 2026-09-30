package com.minebhop.movement;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * KZ-style jump statistics: one chat line per jump, from takeoff to landing.
 *
 * <p>Distance follows the KZ convention of adding the player's width, so the number is the gap
 * you could clear edge to edge rather than how far your centre travelled. Sync is the share of
 * turning ticks where the mouse moved toward the held strafe key -- turning right while holding
 * right is what air acceleration rewards, so a high number means clean strafes. It is averaged over
 * the current hop chain.
 */
public final class JumpStats {

	private boolean active;
	private int lastTick;
	private Vec3 takeoff;
	private double preSpeed;
	private double maxSpeed;
	private int strafes;
	private int lastSide;
	private int turningTicks;
	private int syncedTicks;

	// Sync is reported as the average over the current hop chain, weighted by ticks, so one short
	// hop cannot swing it. A chain ends when you stay on the ground instead of hopping on landing.
	private int chainTurningTicks;
	private int chainSyncedTicks;
	private int landedTick = Integer.MIN_VALUE;

	public void cancel() {
		active = false;
	}

	/**
	 * Called at the start of every Source-handled tick. A skipped tick means vanilla ran in between
	 * -- water, flight, a ladder -- and whatever happened there is not a jump, so drop it.
	 */
	public void checkContinuity(LocalPlayer player) {
		if (active && player.tickCount != lastTick + 1) {
			active = false;
		}
		lastTick = player.tickCount;
	}

	public void takeoff(LocalPlayer player, double horizontalSpeed) {
		if (player.tickCount - landedTick > 1) {
			chainTurningTicks = 0;
			chainSyncedTicks = 0;
		}
		active = true;
		takeoff = player.position();
		preSpeed = horizontalSpeed;
		maxSpeed = horizontalSpeed;
		strafes = 0;
		lastSide = 0;
		turningTicks = 0;
		syncedTicks = 0;
	}

	/**
	 * @param yawDelta   degrees turned since last tick; positive is turning right
	 * @param sideMove   +1 right, -1 left, 0 neither
	 */
	public void airTick(float yawDelta, double sideMove, double horizontalSpeed) {
		if (!active) {
			return;
		}
		int side = (int) Math.signum(sideMove);
		if (side != 0 && side != lastSide) {
			strafes++;
			lastSide = side;
		}
		if (Math.abs(yawDelta) > 0.01f) {
			turningTicks++;
			if (side != 0 && Math.signum(yawDelta) == side) {
				syncedTicks++;
			}
		}
		maxSpeed = Math.max(maxSpeed, horizontalSpeed);
	}

	/** One finished jump. Distance and height in blocks, speeds in u/s, sync in percent. */
	public record Result(double distance, double preSpeed, double maxSpeed, int strafes, int sync, double height) {
	}

	private Result last;

	/** The most recent jump, for the HUD; null before the first landing. */
	public Result last() {
		return last;
	}

	/**
	 * Reports the jump in progress, if any, as landed at the player's current position.
	 *
	 * @param toChat also print it in chat. Off while the HUD is up, which shows it instead: the
	 *               chat lines are long enough to run underneath the speedometer.
	 */
	public void land(LocalPlayer player, boolean toChat) {
		if (!active) {
			return;
		}
		active = false;

		Vec3 landing = player.position();
		double dx = landing.x - takeoff.x;
		double dz = landing.z - takeoff.z;
		double distance = Math.sqrt(dx * dx + dz * dz) + player.getBbWidth();
		double height = landing.y - takeoff.y;
		landedTick = player.tickCount;
		chainTurningTicks += turningTicks;
		chainSyncedTicks += syncedTicks;
		int sync = chainTurningTicks == 0 ? 0 : Math.round(100.0f * chainSyncedTicks / chainTurningTicks);

		last = new Result(distance, preSpeed, maxSpeed, strafes, sync, height);
		if (!toChat) {
			return;
		}

		MutableComponent line = Component.literal("[MineBhop] ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(String.format(Locale.ROOT, "%.2f blocks", distance)).withStyle(ChatFormatting.AQUA))
				.append(stat("pre", String.format(Locale.ROOT, "%.0f", preSpeed)))
				.append(stat("max", String.format(Locale.ROOT, "%.0f", maxSpeed)))
				.append(stat("strafes", Integer.toString(strafes)))
				.append(stat("sync", sync + "%"));
		if (Math.abs(height) > 0.01) {
			line.append(stat("height", String.format(Locale.ROOT, "%+.2f", height)));
		}
		player.sendSystemMessage(line);
	}

	private static Component stat(String label, String value) {
		return Component.literal(" | " + label + " ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}
}
