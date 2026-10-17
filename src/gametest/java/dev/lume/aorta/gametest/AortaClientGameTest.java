package dev.lume.aorta.gametest;

import dev.lume.aorta.Aorta;
import dev.lume.aorta.body.BodyInputs;
import dev.lume.aorta.body.BodyModel;
import dev.lume.aorta.client.AortaClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Smoke-tests Aorta on the real client (audio cannot be asserted in a headless
 * run, but the wiring can): the sound events must be registered, the model must
 * produce a sane resting state, the client entrypoint must have built the driver,
 * and a simulated beat must play without throwing.
 */
@SuppressWarnings("UnstableApiUsage")
public class AortaClientGameTest implements FabricClientGameTest {

	private static final Logger LOGGER = LoggerFactory.getLogger("aorta-gametest");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			// The common entrypoint must have registered the sound events.
			context.runOnClient(mc -> {
				check(Aorta.LUB != null && "aorta:lub".equals(Aorta.LUB.location().toString()),
						"lub not registered");
				check(Aorta.DUB != null && "aorta:dub".equals(Aorta.DUB.location().toString()),
						"dub not registered");
				check(Aorta.BREATH_IN != null, "breath_in not registered");
				check(Aorta.BREATH_OUT != null, "breath_out not registered");
			});

			// The model must settle on a sane resting state.
			double hr = context.computeOnClient(mc -> BodyModel.heartRate(BodyInputs.resting()));
			double br = context.computeOnClient(mc -> BodyModel.breathRate(BodyInputs.resting()));
			LOGGER.info("[aorta-gametest] resting model: HR={} BR={}", hr, br);
			check(hr >= 52.0 && hr <= 64.0, "resting heart rate out of range: " + hr);
			check(br >= 10.0 && br <= 16.0, "resting breath rate out of range: " + br);

			// The client entrypoint must have wired the driver.
			boolean wired = context.computeOnClient(mc -> AortaClient.driver() != null);
			check(wired, "client driver not initialised");

			// Force a beat and let a few ticks elapse; this must not throw.
			context.runOnClient(mc -> AortaClient.driver().simulate());
			context.waitTicks(20);

			context.takeScreenshot("aorta-smoke");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("[aorta-gametest] " + message);
		}
	}
}
