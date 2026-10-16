package dev.lume.aorta.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A single non-looping sound played straight into the ears: no spatial position,
 * no occlusion, no doppler. The sound is inside the player, so it is set relative
 * with {@link SoundInstance.Attenuation#NONE} and does not move with the world.
 */
public final class PulseSound extends AbstractSoundInstance {

	public PulseSound(SoundEvent event, double volume, double pitch) {
		super(event, SoundSource.AMBIENT, RandomSource.create());
		this.volume = (float) volume;
		this.pitch = (float) pitch;
		this.looping = false;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.relative = true;
	}
}
