package com.minebhop.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;

/**
 * A surf ramp: the slope of a straight, right-way-up stair block.
 *
 * <p>Minecraft has no slopes, so a run of stairs stands in for one. The ramp is the plane through
 * the tips of the steps, rising toward the stair's {@code FACING} at 45 degrees. Every part of the
 * stair's real collision shape lies on or below that plane, so a player resting on it never
 * overlaps a block: to anything that checks collision, server included, they are simply in the air.
 * Stairs stacked one up and one back continue the same plane, so a staircase is one long ramp.
 */
final class SurfRamp {

	/** How far above the plane the feet may be and still count as touching it. */
	private static final double CONTACT = 0.1;
	/** How far below it they may be, after a fast tick, before it counts as being under the ramp. */
	private static final double DEPTH = 0.75;

	private final int stepX;
	private final int stepZ;
	private final int blockY;
	/** The block centre's position along the facing direction. */
	private final double centre;
	/** Unit normal of the plane, pointing up and away from the high side. */
	final double normalX;
	final double normalY;
	final double normalZ;

	private SurfRamp(Direction facing, BlockPos pos) {
		this.stepX = facing.getStepX();
		this.stepZ = facing.getStepZ();
		this.blockY = pos.getY();
		this.centre = (pos.getX() + 0.5) * stepX + (pos.getZ() + 0.5) * stepZ;
		double inverseRoot2 = 1.0 / Math.sqrt(2.0);
		this.normalX = -stepX * inverseRoot2;
		this.normalY = inverseRoot2;
		this.normalZ = -stepZ * inverseRoot2;
	}

	/**
	 * Height of the plane under a player box centred at {@code (x, z)} with half-width {@code half}.
	 * Measured at the box's edge on the high side, which is the part that touches a rising slope.
	 * Not limited to this block, so a move along a staircase stays on its one plane.
	 */
	double heightAt(double x, double z, double half) {
		double along = x * stepX + z * stepZ + half;
		return blockY + 0.5 + (along - (centre - 0.5));
	}

	/**
	 * The highest ramp the player is touching, or will reach this tick, or null when there is none.
	 *
	 * @param fall this tick's vertical movement in blocks. A fast fall covers more than the contact
	 *             margin in one tick, so without looking ahead the player would land on the steps
	 *             the tick before the ramp was ever seen -- with the fall damage and dead stop that
	 *             come with landing.
	 */
	static SurfRamp under(LocalPlayer player, double fall) {
		AABB box = player.getBoundingBox();
		double half = (box.maxX - box.minX) / 2.0;
		double x = (box.minX + box.maxX) / 2.0;
		double z = (box.minZ + box.maxZ) / 2.0;

		SurfRamp best = null;
		double bestHeight = Double.NEGATIVE_INFINITY;
		for (int bx = Mth.floor(box.minX); bx <= Mth.floor(box.maxX - 1.0e-7); bx++) {
			for (int bz = Mth.floor(box.minZ); bz <= Mth.floor(box.maxZ - 1.0e-7); bz++) {
				for (int by = Mth.floor(box.minY - 1.5 + Math.min(fall, 0.0)); by <= Mth.floor(box.minY + 0.5); by++) {
					BlockPos pos = new BlockPos(bx, by, bz);
					BlockState state = player.level().getBlockState(pos);
					if (!(state.getBlock() instanceof StairBlock)
							|| state.getValue(StairBlock.HALF) != Half.BOTTOM
							|| state.getValue(StairBlock.SHAPE) != StairsShape.STRAIGHT) {
						continue;
					}
					SurfRamp ramp = new SurfRamp(state.getValue(StairBlock.FACING), pos);
					// Within this block, for deciding contact; past its edges belongs to the next stair.
					double along = Mth.clamp(x * ramp.stepX + z * ramp.stepZ + half - (ramp.centre - 0.5), 0.0, 1.0);
					double height = by + 0.5 + along;
					if (box.minY + Math.min(fall, 0.0) <= height + CONTACT && box.minY >= height - DEPTH
							&& height > bestHeight) {
						best = ramp;
						bestHeight = height;
					}
				}
			}
		}
		return best;
	}
}
