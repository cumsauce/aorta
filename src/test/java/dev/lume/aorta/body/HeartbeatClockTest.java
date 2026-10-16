package dev.lume.aorta.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatClockTest {

	@Test
	void producesLubAndDubAcrossACycle() {
		HeartbeatClock clock = new HeartbeatClock();
		boolean sawLub = false;
		boolean sawDub = false;
		for (long t = 0; t < 1200; t++) {
			HeartbeatClock.Beat b = clock.tick(t, 70.0);
			sawLub |= b == HeartbeatClock.Beat.LUB;
			sawDub |= b == HeartbeatClock.Beat.DUB;
		}
		assertTrue(sawLub, "never saw a lub");
		assertTrue(sawDub, "never saw a dub");
	}

	@Test
	void higherRateYieldsMoreBeatsPerMinute() {
		int slow = countLubs(new HeartbeatClock(), 1200, 70.0);
		int fast = countLubs(new HeartbeatClock(), 1200, 140.0);
		assertTrue(fast > slow, "fast=" + fast + " should exceed slow=" + slow);
	}

	@Test
	void zeroRateYieldsNothing() {
		HeartbeatClock clock = new HeartbeatClock();
		for (long t = 0; t < 100; t++) {
			assertEquals(null, clock.tick(t, 0.0));
		}
	}

	@Test
	void firstTickIsALub() {
		assertEquals(HeartbeatClock.Beat.LUB, new HeartbeatClock().tick(0, 70.0));
	}

	private int countLubs(HeartbeatClock clock, long ticks, double hr) {
		int n = 0;
		for (long t = 0; t < ticks; t++) {
			if (clock.tick(t, hr) == HeartbeatClock.Beat.LUB) {
				n++;
			}
		}
		return n;
	}
}
