package dev.lume.aorta.client;

import dev.lume.aorta.config.AortaConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A bare-bones settings screen: a volume slider and a heartbeat on/off toggle.
 * Volume is previewed live (the driver reads the config every tick) and persisted
 * when the screen closes, so dragging does not rewrite the JSON on every pixel.
 */
public final class AortaScreen extends Screen {

	private static final int COLUMN_WIDTH = 200;

	private final Screen parent;

	public AortaScreen(Screen parent) {
		super(Component.literal("Aorta"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - COLUMN_WIDTH / 2;

		int titleWidth = this.font.width(this.title);
		addRenderableWidget(new StringWidget(this.width / 2 - titleWidth / 2, 40, titleWidth, 20, this.title, this.font));
		addRenderableWidget(new VolumeSlider(x, 72, COLUMN_WIDTH, 20, AortaConfig.get().volume()));
		addRenderableWidget(Button.builder(enabledLabel(), button -> {
			AortaConfig.set(AortaConfig.get().withEnabled(!AortaConfig.get().enabled()));
			button.setMessage(enabledLabel());
		}).bounds(x, 104, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(x, this.height - 40, COLUMN_WIDTH, 20).build());
	}

	private static Component enabledLabel() {
		return Component.literal("Heartbeat: " + (AortaConfig.get().enabled() ? "on" : "off"));
	}

	@Override
	public void onClose() {
		AortaConfig.get().save();
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world (and the body sounds) running so the slider is audible.
		return false;
	}

	private static final class VolumeSlider extends AbstractSliderButton {

		VolumeSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, Component.empty(), value);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Volume: " + Math.round(this.value * 100.0) + "%"));
		}

		@Override
		protected void applyValue() {
			AortaConfig.apply(AortaConfig.get().withVolume(this.value));
		}
	}
}
