package dev.lume.aorta.client;

import dev.lume.aorta.Aorta;
import dev.lume.aorta.body.BodyInputs;
import dev.lume.aorta.body.BodyModel;
import dev.lume.aorta.body.BreathClock;
import dev.lume.aorta.body.HeartbeatClock;
import dev.lume.aorta.config.AortaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The heartbeat: samples the player's game state every tick, feeds it through the
 * model, advances the two clocks, and plays the resulting one-shot sounds. This is
 * the only place Minecraft types meet the pure model in {@code dev.lume.aorta.body}.
 */
public final class BodyDriver {

	private static final double THREAT_RADIUS = 24.0;

	private final HeartbeatClock heartbeat = new HeartbeatClock();
	private final BreathClock breath = new BreathClock();

	private Minecraft mc;
	private long tick;

	private double lastHeartRate = BodyModel.RESTING_HR;
	private double lastBreathRate = BodyModel.RESTING_BR;

	public double lastHeartRate() {
		return lastHeartRate;
	}

	public double lastBreathRate() {
		return lastBreathRate;
	}

	public void onTick(Minecraft client) {
		mc = client;
		tick++;

		if (mc.isPaused()) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		if (!AortaConfig.get().enabled()) {
			return;
		}

		BodyInputs in = sample(player);

		double heartRate = BodyModel.heartRate(in);
		double breathRate = BodyModel.breathRate(in);
		lastHeartRate = heartRate;
		lastBreathRate = breathRate;

		HeartbeatClock.Beat beat = heartbeat.tick(tick, heartRate);
		if (beat != null) {
			SoundEvent event = beat == HeartbeatClock.Beat.LUB ? Aorta.LUB : Aorta.DUB;
			double force = BodyModel.heartForce(in);
			// Louder hearts beat slightly higher-pitched; the dub is already higher
			// than the lub in the synthesized asset itself.
			play(event, force, 0.90 + 0.18 * force);
		}

		BreathClock.Phase phase = breath.tick(tick, breathRate);
		if (phase != null) {
			SoundEvent event = phase == BreathClock.Phase.INHALE ? Aorta.BREATH_IN : Aorta.BREATH_OUT;
			play(event, BodyModel.breathDepth(in), 1.0);
		}
	}

	/** Play a beat immediately, for the {@code /aorta simulate} debug command. */
	public void simulate() {
		if (mc == null) {
			return;
		}
		double master = AortaConfig.get().volume();
		mc.getSoundManager().play(new PulseSound(Aorta.LUB, 0.8 * master, 1.0));
		mc.getSoundManager().play(new PulseSound(Aorta.DUB, 0.6 * master, 1.0));
	}

	private BodyInputs sample(LocalPlayer player) {
		double health = player.getHealth();
		double maxHealth = player.getMaxHealth();
		int food = player.getFoodData().getFoodLevel();
		float saturation = player.getFoodData().getSaturationLevel();
		int air = player.getAirSupply();
		int maxAir = player.getMaxAirSupply();
		boolean underWater = player.isUnderWater();
		boolean sprinting = player.isSprinting();
		boolean moving = player.getDeltaMovement().lengthSqr() > 1.0e-6;
		boolean falling = player.getDeltaMovement().y < -0.3;
		double threat = sampleThreat(player);
		int light = mc.level.getMaxLocalRawBrightness(player.blockPosition());
		int depth = depthOf(player);

		return new BodyInputs(health, maxHealth, food, saturation, air, maxAir,
				underWater, sprinting, moving, falling, threat, light, depth);
	}

	private double sampleThreat(LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(THREAT_RADIUS);
		double nearest = THREAT_RADIUS;
		boolean any = false;
		for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box, e -> e instanceof Enemy)) {
			any = true;
			nearest = Math.min(nearest, player.distanceTo(e));
		}
		if (!any) {
			return 0.0;
		}
		return BodyModel.clamp(1.0 - nearest / THREAT_RADIUS, 0.0, 1.0);
	}

	private int depthOf(LocalPlayer player) {
		int surfaceY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, player.blockPosition());
		return Math.max(0, surfaceY - player.blockPosition().getY());
	}

	private void play(SoundEvent event, double strength, double pitch) {
		SoundManager sounds = mc.getSoundManager();
		double master = AortaConfig.get().volume();
		sounds.play(new PulseSound(event, strength * master, pitch));
	}
}
