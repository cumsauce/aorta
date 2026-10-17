package dev.lume.aorta.body;

/**
 * First-order easing toward a target, with separate time constants for rising and
 * falling. The model answers "what rate does this moment call for"; the smoother
 * answers "how fast can the body actually get there". Without it a single tick of
 * sprinting snaps the heart from rest to a hundred beats and back again.
 *
 * <p>Pure and tick-based: {@code k} is the fraction of the remaining gap closed
 * per update, so a smaller {@code k} is a slower response.
 */
public final class RateSmoother {

	private final double attack;
	private final double release;
	private double value;

	public RateSmoother(double initial, double attack, double release) {
		this.value = initial;
		this.attack = clamp01(attack);
		this.release = clamp01(release);
	}

	/** Move one step toward {@code target}; returns the new value. */
	public double update(double target) {
		double k = target > value ? attack : release;
		value += (target - value) * k;
		return value;
	}

	public double value() {
		return value;
	}

	public void reset(double v) {
		value = v;
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
