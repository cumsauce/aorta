package dev.lume.aorta.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateSmootherTest {

	@Test
	void easesTowardATarget() {
		RateSmoother s = new RateSmoother(58.0, 0.04, 0.012);
		double afterOne = s.update(113.0);
		assertTrue(afterOne > 58.0 && afterOne < 113.0, "should move partway: " + afterOne);
	}

	@Test
	void takesManyTicksToReachTheTarget() {
		RateSmoother s = new RateSmoother(58.0, 0.04, 0.012);
		for (int i = 0; i < 10; i++) {
			s.update(113.0);
		}
		assertTrue(s.value() < 100.0, "ten ticks should not be enough: " + s.value());
	}

	@Test
	void fallsMoreSlowlyThanItRises() {
		RateSmoother rising = new RateSmoother(58.0, 0.04, 0.012);
		RateSmoother falling = new RateSmoother(113.0, 0.04, 0.012);
		for (int i = 0; i < 20; i++) {
			rising.update(113.0);
			falling.update(58.0);
		}
		double rise = rising.value() - 58.0;
		double fall = 113.0 - falling.value();
		assertTrue(fall < rise, "cool-down should lag warm-up: rise=" + rise + " fall=" + fall);
	}

	@Test
	void settlesOnTheTargetEventually() {
		RateSmoother s = new RateSmoother(58.0, 0.04, 0.012);
		for (int i = 0; i < 2000; i++) {
			s.update(100.0);
		}
		assertEquals(100.0, s.value(), 0.01);
	}
}
