package dev.lume.aorta.client;

import dev.lume.aorta.Aorta;
import dev.lume.aorta.body.BodyInputs;
import dev.lume.aorta.body.BodyModel;
import dev.lume.aorta.body.BreathClock;
import dev.lume.aorta.body.HeartbeatClock;
import dev.lume.aorta.body.RateSmoother;
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

	// Per-tick easing. Rising is quick, falling is slow: the heart recovers from
	// exertion far more gradually than it ramps into it.
	private static final double HR_ATTACK = 0.040;
	private static final double HR_RELEASE = 0.012;
	private static final double BR_ATTACK = 0.070;
	private static final double BR_RELEASE = 0.022;

	private final HeartbeatClock heartbeat = new HeartbeatClock();
	private final BreathClock breath = new BreathClock();
	private final RateSmoother heartRate = new RateSmoother(BodyModel.RESTING_HR, HR_ATTACK, HR_RELEASE);
	private final RateSmoother breathRate = new RateSmoother(BodyModel.RESTING_BR, BR_ATTACK, BR_RELEASE);

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

		double hr = heartRate.update(BodyModel.heartRate(in));
		lastHeartRate = hr;

		HeartbeatClock.Beat beat = heartbeat.tick(tick, hr);
		if (beat != null) {
			SoundEvent event = beat == HeartbeatClock.Beat.LUB ? Aorta.LUB : Aorta.DUB;
			double force = BodyModel.heartForce(in);
			// Louder hearts beat slightly higher-pitched; the dub is already higher
			// than the lub in the synthesized asset itself.
			play(event, force, 0.90 + 0.18 * force);
		}

		if (in.underWater()) {
			// Hold your breath: no respiratory sound while submerged. Resetting the
			// smoother means surfacing starts from silence and eases back in.
			breathRate.reset(0.0);
			lastBreathRate = 0.0;
		} else {
			double br = breathRate.update(BodyModel.breathRate(in));
			lastBreathRate = br;

			BreathClock.Phase phase = breath.tick(tick, br);
			if (phase != null) {
				SoundEvent event = phase == BreathClock.Phase.INHALE ? Aorta.BREATH_IN : Aorta.BREATH_OUT;
				play(event, BodyModel.breathDepth(in), 1.0);
			}
		}
	}

	/** Play a beat immediately, for the {@code /aorta simulate} debug command. */
	public void simulate() {
		if (mc == null) {
			return;
		}
		double level = AortaConfig.get().volume() * 0.35;
		mc.getSoundManager().play(new PulseSound(Aorta.LUB, level, 1.0));
		mc.getSoundManager().play(new PulseSound(Aorta.DUB, level * 0.75, 1.0));
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
		// Squared response: calm states fall away much faster than loud ones, so
		// standing still is nearly silent while a sprint still carries.
		double volume = master * strength * strength;
		sounds.play(new PulseSound(event, volume, pitch));
	}
}
