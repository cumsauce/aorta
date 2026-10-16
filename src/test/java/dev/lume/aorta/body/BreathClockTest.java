package dev.lume.aorta.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreathClockTest {

	@Test
	void producesInhaleAndExhaleAcrossACycle() {
		BreathClock clock = new BreathClock();
		boolean sawIn = false;
		boolean sawOut = false;
		for (long t = 0; t < 2400; t++) {
			BreathClock.Phase p = clock.tick(t, 14.0);
			sawIn |= p == BreathClock.Phase.INHALE;
			sawOut |= p == BreathClock.Phase.EXHALE;
		}
		assertTrue(sawIn, "never saw an inhale");
		assertTrue(sawOut, "never saw an exhale");
	}

	@Test
	void higherRateYieldsMoreCyclesPerMinute() {
		int slow = countInhales(new BreathClock(), 2400, 14.0);
		int fast = countInhales(new BreathClock(), 2400, 28.0);
		assertTrue(fast > slow, "fast=" + fast + " should exceed slow=" + slow);
	}

	@Test
	void zeroRateYieldsNothing() {
		BreathClock clock = new BreathClock();
		for (long t = 0; t < 100; t++) {
			assertNull(clock.tick(t, 0.0));
		}
	}

	private int countInhales(BreathClock clock, long ticks, double br) {
		int n = 0;
		for (long t = 0; t < ticks; t++) {
			if (clock.tick(t, br) == BreathClock.Phase.INHALE) {
				n++;
			}
		}
		return n;
	}
}
