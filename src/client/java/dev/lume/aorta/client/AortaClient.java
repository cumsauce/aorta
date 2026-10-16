package dev.lume.aorta.client;

import dev.lume.aorta.client.command.AortaCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint. Wires the per-tick driver and the {@code /aorta} command.
 * The driver is a singleton so both the tick hook and the command can reach it.
 */
public final class AortaClient implements ClientModInitializer {

	private static BodyDriver driver;

	public static BodyDriver driver() {
		return driver;
	}

	@Override
	public void onInitializeClient() {
		driver = new BodyDriver();
		ClientTickEvents.END_CLIENT_TICK.register(client -> driver.onTick(client));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> AortaCommand.register(dispatcher));
	}
}
