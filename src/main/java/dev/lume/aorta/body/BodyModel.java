package dev.lume.aorta.body;

/**
 * The physiological model: game state in, heart and breath parameters out.
 *
 * <p>Every term is closed-form and deliberately simple -- each driver adds a
 * bounded contribution to a resting value, so the whole thing stays readable and
 * the tests can pin the numbers. There are no hidden states or time integrals
 * here; the easing that keeps the rhythm from lurching lives in
 * {@link RateSmoother}, and the rhythm itself in {@link HeartbeatClock} and
 * {@link BreathClock}.
 */
public final class BodyModel {

	/** Beats per minute at rest. Deliberately calm: 58 reads as settled, not idle. */
	public static final double RESTING_HR = 58.0;
	/** Breaths per minute at rest. */
	public static final double RESTING_BR = 12.0;

	private BodyModel() {
	}

	/** Heart rate in beats per minute. */
	public static double heartRate(BodyInputs in) {
		double hr = RESTING_HR;

		// Low health stresses the heart: up to +50 bpm as health runs out.
		hr += (1.0 - healthFraction(in)) * 50.0;

		// Hunger: starving weakens, mild hunger is slightly agitating.
		if (in.foodLevel() <= 3) {
			hr += 16.0;
		} else if (in.foodLevel() <= 9) {
			hr += 5.0;
		}

		// Exertion.
		if (in.sprinting()) {
			hr += 50.0;
		} else if (in.moving()) {
			hr += 18.0;
		}

		// Drowning: panic rises as the air runs out.
		if (in.underWater()) {
			hr += (1.0 - airFraction(in)) * 45.0;
		}

		// Nearby hostiles.
		hr += in.threat() * 34.0;

		if (in.falling()) {
			hr += 12.0;
		}

		return hr;
	}

	/**
	 * Breathing rate in breaths per minute. Zero while submerged: the body holds
	 * its breath rather than breathing water. Surfacing resumes the rhythm.
	 */
	public static double breathRate(BodyInputs in) {
		if (in.underWater()) {
			return 0.0;
		}

		double br = RESTING_BR;

		if (in.sprinting()) {
			br += 20.0;
		} else if (in.moving()) {
			br += 7.0;
		}

		br += in.threat() * 10.0;
		br += (1.0 - healthFraction(in)) * 8.0;

		return br;
	}

	/** Breathing depth 0..1: how loud each breath is (chest volume vs shallow). */
	public static double breathDepth(BodyInputs in) {
		// Quiet at rest; the model's job is to stay out of the way when calm.
		double depth = 0.35;

		if (in.sprinting()) {
			depth = 1.0;
		} else if (in.moving()) {
			depth = 0.65;
		}

		return clamp(depth, 0.0, 1.0);
	}

	/** Heart force 0..1: how hard (and loud) each lub-dub is. */
	public static double heartForce(BodyInputs in) {
		double force = 0.48;

		if (in.sprinting()) {
			force = 1.0;
		} else if (in.moving()) {
			force = 0.72;
		}

		force += in.threat() * 0.30;
		force += (1.0 - healthFraction(in)) * 0.20;

		return clamp(force, 0.0, 1.0);
	}

	private static double healthFraction(BodyInputs in) {
		return in.maxHealth() <= 0.0 ? 1.0 : in.health() / in.maxHealth();
	}

	private static double airFraction(BodyInputs in) {
		return in.maxAirSupply() <= 0 ? 1.0 : (double) in.airSupply() / in.maxAirSupply();
	}

	public static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
