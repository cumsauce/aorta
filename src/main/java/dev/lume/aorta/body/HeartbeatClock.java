package dev.lume.aorta.body;

/**
 * Turns a heart rate into a stream of lub/dub beats, scheduled on integer game
 * ticks. Pure and deterministic: the only state is the next tick each sound is
 * due, so the whole thing is trivially testable.
 *
 * <p>Each cardiac cycle is a {@code lub} (S1) followed, a little later, by a
 * {@code dub} (S2). The cycle length is {@code 1200 / heartRate} ticks (20 tps),
 * and the dub lands about 28% of the way through the cycle -- the physiological
 * S1-to-S2 gap.
 */
public final class HeartbeatClock {

	private static final double TICKS_PER_MINUTE = 20.0 * 60.0; // 1200

	private long nextLubTick;
	private long nextDubTick = Long.MAX_VALUE;

	/** Advance one tick; returns the beat due this tick, or {@code null}. */
	public Beat tick(long now, double heartRate) {
		if (heartRate <= 0.0) {
			return null;
		}

		double interval = TICKS_PER_MINUTE / heartRate;
		if (interval < 4.0) {
			interval = 4.0; // a hard ceiling around 300 bpm
		}

		if (now >= nextLubTick) {
			long whole = (long) interval;
			nextLubTick = now + whole;
			nextDubTick = now + Math.max(1, (long) (whole * 0.28));
			return Beat.LUB;
		}

		if (now >= nextDubTick) {
			nextDubTick = Long.MAX_VALUE; // consumed; the next lub reschedules it
			return Beat.DUB;
		}

		return null;
	}

	public void reset() {
		nextLubTick = 0;
		nextDubTick = Long.MAX_VALUE;
	}

	public enum Beat {
		LUB,
		DUB
	}
}
