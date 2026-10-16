package dev.lume.aorta.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyModelTest {

	@Test
	void restingHeartRateIsExactlySeventy() {
		assertEquals(70.0, BodyModel.heartRate(BodyInputs.resting()), 1e-9);
	}

	@Test
	void sprintingRaisesHeartAndBreath() {
		BodyInputs run = new BodyInputs(20, 20, 20, 5f, 300, 300, false, true, true, false, 0.0, 15, 0);
		assertTrue(BodyModel.heartRate(run) > BodyModel.heartRate(BodyInputs.resting()));
		assertTrue(BodyModel.breathRate(run) > BodyModel.breathRate(BodyInputs.resting()));
	}

	@Test
	void drowningRaisesBoth() {
		BodyInputs drown = new BodyInputs(20, 20, 20, 5f, 30, 300, true, false, false, false, 0.0, 15, 0);
		assertTrue(BodyModel.heartRate(drown) > BodyModel.RESTING_HR);
		assertTrue(BodyModel.breathRate(drown) > BodyModel.RESTING_BR);
	}

	@Test
	void threatRaisesHeartRate() {
		BodyInputs scared = new BodyInputs(20, 20, 20, 5f, 300, 300, false, false, false, false, 1.0, 15, 0);
		assertTrue(BodyModel.heartRate(scared) > BodyModel.heartRate(BodyInputs.resting()));
	}

	@Test
	void breathDepthIsBounded() {
		for (double threat : new double[] {0.0, 0.5, 1.0}) {
			BodyInputs in = new BodyInputs(20, 20, 20, 5f, 300, 300, false, true, true, false, threat, 15, 0);
			double depth = BodyModel.breathDepth(in);
			assertTrue(depth >= 0.0 && depth <= 1.0, "depth out of range: " + depth);
		}
	}

	@Test
	void heartForceIsBounded() {
		BodyInputs max = new BodyInputs(1, 20, 0, 0f, 0, 300, true, true, true, true, 1.0, 0, 64);
		double force = BodyModel.heartForce(max);
		assertTrue(force >= 0.0 && force <= 1.0, "force out of range: " + force);
	}
}
