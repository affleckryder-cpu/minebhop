package com.minebhop.movement;

/**
 * A mutable 3-component velocity in Source units per second.
 *
 * <p>Mutable on purpose: the movement functions are ports of Source's {@code CGameMovement},
 * which mutates {@code mv->m_vecVelocity} in place, and sub-stepped air acceleration runs
 * this up to 7 times a tick.
 */
public final class SrcVec {
	public double x;
	public double y;
	public double z;

	public SrcVec(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public void set(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Horizontal speed, which is what every Source speed rule is expressed in. */
	public double horizontalSpeed() {
		return Math.sqrt(x * x + z * z);
	}

	public void scaleHorizontal(double factor) {
		x *= factor;
		z *= factor;
	}
}
