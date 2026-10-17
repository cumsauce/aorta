package dev.lume.aorta.client.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.lume.aorta.body.BodyModel;
import dev.lume.aorta.client.AortaClient;
import dev.lume.aorta.client.AortaScreen;
import dev.lume.aorta.client.BodyDriver;
import dev.lume.aorta.config.AortaConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * The client-side {@code /aorta} command: a small debugging surface that shows the
 * live model state, opens the settings screen, forces a beat, and reloads the config.
 */
public final class AortaCommand {

	private AortaCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(
				ClientCommands.literal("aorta")
						.then(ClientCommands.literal("menu").executes(ctx -> menu(ctx.getSource())))
						.then(ClientCommands.literal("status").executes(ctx -> status(ctx.getSource())))
						.then(ClientCommands.literal("simulate").executes(ctx -> simulate(ctx.getSource())))
						.then(ClientCommands.literal("reload").executes(ctx -> reload(ctx.getSource()))));
	}

	private static int menu(FabricClientCommandSource src) {
		src.getClient().setScreenAndShow(new AortaScreen(null));
		return 1;
	}

	private static int status(FabricClientCommandSource src) {
		AortaConfig c = AortaConfig.get();
		BodyDriver d = AortaClient.driver();
		String hr = d == null ? "?" : Integer.toString((int) d.lastHeartRate());
		String br = d == null ? "?" : Integer.toString((int) d.lastBreathRate());
		src.sendFeedback(Component.literal("Aorta " + (c.enabled() ? "on" : "off")
				+ " · volume " + Math.round(c.volume() * 100.0) + "%"
				+ " · heart " + hr + " bpm · breath " + br + " /min"
				+ " (rest " + (int) BodyModel.RESTING_HR + "/" + (int) BodyModel.RESTING_BR + ")"));
		return 1;
	}

	private static int simulate(FabricClientCommandSource src) {
		BodyDriver d = AortaClient.driver();
		if (d == null) {
			src.sendError(Component.literal("Aorta driver is not ready."));
			return 0;
		}
		d.simulate();
		src.sendFeedback(Component.literal("Simulated a heartbeat."));
		return 1;
	}

	private static int reload(FabricClientCommandSource src) {
		AortaConfig.reload();
		AortaConfig c = AortaConfig.get();
		src.sendFeedback(Component.literal("Aorta config reloaded: "
				+ (c.enabled() ? "on" : "off") + ", volume " + Math.round(c.volume() * 100.0) + "%"));
		return 1;
	}
}
