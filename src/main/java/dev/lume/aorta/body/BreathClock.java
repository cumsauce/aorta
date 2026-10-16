package dev.lume.aorta.body;

/**
 * Turns a breathing rate into a stream of inhale/exhale events. Pure and
 * deterministic, mirroring {@link HeartbeatClock}.
 *
 * <p>Each respiratory cycle is {@code 1200 / breathRate} ticks; the inhale takes
 * the first ~42% of the cycle and the exhale the rest, which matches the normal
 * longer-exhale rhythm.
 */
public final class BreathClock {

	private static final double TICKS_PER_MINUTE = 20.0 * 60.0; // 1200

	private long nextInhaleTick;
	private long nextExhaleTick = Long.MAX_VALUE;

	/** Advance one tick; returns the phase due this tick, or {@code null}. */
	public Phase tick(long now, double breathRate) {
		if (breathRate <= 0.0) {
			return null;
		}

		double cycle = TICKS_PER_MINUTE / breathRate;
		if (cycle < 40.0) {
			cycle = 40.0; // a hard ceiling around 30 breaths/min
		}

		if (now >= nextInhaleTick) {
			long whole = (long) cycle;
			nextInhaleTick = now + whole;
			nextExhaleTick = now + Math.max(10, (long) (whole * 0.42));
			return Phase.INHALE;
		}

		if (now >= nextExhaleTick) {
			nextExhaleTick = Long.MAX_VALUE;
			return Phase.EXHALE;
		}

		return null;
	}

	public void reset() {
		nextInhaleTick = 0;
		nextExhaleTick = Long.MAX_VALUE;
	}

	public enum Phase {
		INHALE,
		EXHALE
	}
}
