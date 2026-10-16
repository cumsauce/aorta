package dev.lume.aorta.body;

/**
 * The physiological model: game state in, heart and breath parameters out.
 *
 * <p>Every term is closed-form and deliberately simple -- each driver adds a
 * bounded contribution to a resting value, so the whole thing stays readable and
 * the tests can pin the numbers. There are no hidden states or time integrals
 * here; rhythm lives in {@link HeartbeatClock} and {@link BreathClock}.
 */
public final class BodyModel {

	/** Beats per minute at rest. */
	public static final double RESTING_HR = 70.0;
	/** Breaths per minute at rest. */
	public static final double RESTING_BR = 14.0;

	private BodyModel() {
	}

	/** Heart rate in beats per minute. */
	public static double heartRate(BodyInputs in) {
		double hr = RESTING_HR;

		// Low health stresses the heart: up to +60 bpm as health runs out.
		hr += (1.0 - healthFraction(in)) * 60.0;

		// Hunger: starving weakens, mild hunger is slightly agitating.
		if (in.foodLevel() <= 3) {
			hr += 18.0;
		} else if (in.foodLevel() <= 9) {
			hr += 6.0;
		}

		// Exertion.
		if (in.sprinting()) {
			hr += 55.0;
		} else if (in.moving()) {
			hr += 22.0;
		}

		// Drowning: panic rises as the air runs out.
		if (in.underWater()) {
			hr += (1.0 - airFraction(in)) * 50.0;
		}

		// Nearby hostiles.
		hr += in.threat() * 40.0;

		if (in.falling()) {
			hr += 15.0;
		}

		return hr;
	}

	/** Breathing rate in breaths per minute. */
	public static double breathRate(BodyInputs in) {
		double br = RESTING_BR;

		if (in.sprinting()) {
			br += 22.0;
		} else if (in.moving()) {
			br += 8.0;
		}

		if (in.underWater()) {
			br += (1.0 - airFraction(in)) * 18.0;
		}

		br += in.threat() * 12.0;
		br += (1.0 - healthFraction(in)) * 10.0;

		return br;
	}

	/** Breathing depth 0..1: how loud each breath is (chest volume vs shallow). */
	public static double breathDepth(BodyInputs in) {
		double depth = 0.45; // shallow and quiet at rest

		if (in.sprinting()) {
			depth = 1.0;
		} else if (in.moving()) {
			depth = 0.75;
		}

		if (in.underWater()) {
			// Desperate, ragged breaths that never quite fill.
			depth = 0.35 + (1.0 - airFraction(in)) * 0.4;
		}

		return clamp(depth, 0.0, 1.0);
	}

	/** Heart force 0..1: how hard (and loud) each lub-dub is. */
	public static double heartForce(BodyInputs in) {
		double force = 0.55;

		if (in.sprinting()) {
			force = 1.0;
		} else if (in.moving()) {
			force = 0.8;
		}

		force += in.threat() * 0.35;
		force += (1.0 - healthFraction(in)) * 0.25;

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
