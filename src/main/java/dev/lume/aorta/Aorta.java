package dev.lume.aorta;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aorta's main entrypoint. The mod has no server-side logic: the only job of the
 * common side is to register the sound events the client plays, so both sides
 * agree on the event table. Everything audible lives on the client.
 */
public final class Aorta implements ModInitializer {

	public static final String MOD_ID = "aorta";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** S1, the first heart sound ("lub"). */
	public static final SoundEvent LUB = register("lub");
	/** S2, the second heart sound ("dub"). */
	public static final SoundEvent DUB = register("dub");
	/** An inhalation, the rising half of a breath. */
	public static final SoundEvent BREATH_IN = register("breath_in");
	/** An exhalation, the falling half of a breath. */
	public static final SoundEvent BREATH_OUT = register("breath_out");

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	@Override
	public void onInitialize() {
		// Sound events are registered statically above; there is nothing else to do
		// on the common side.
	}
}
