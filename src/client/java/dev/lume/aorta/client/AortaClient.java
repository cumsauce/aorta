package dev.lume.aorta.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lume.aorta.client.command.AortaCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Client entrypoint. Wires the per-tick driver, the settings keybind and the
 * {@code /aorta} command. The driver is a singleton so both the tick hook and the
 * command can reach it.
 */
public final class AortaClient implements ClientModInitializer {

	private static BodyDriver driver;
	private static KeyMapping openSettings;

	public static BodyDriver driver() {
		return driver;
	}

	@Override
	public void onInitializeClient() {
		driver = new BodyDriver();

		openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aorta.menu", InputConstants.KEY_F9, KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			driver.onTick(client);
			while (openSettings.consumeClick()) {
				client.setScreenAndShow(new AortaScreen(null));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> AortaCommand.register(dispatcher));
	}
}
