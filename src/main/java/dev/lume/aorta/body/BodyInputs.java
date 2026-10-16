package dev.lume.aorta.body;

/**
 * The game state Aorta samples each tick, distilled into the handful of numbers
 * the physiological model needs. Deliberately pure data: no Minecraft classes, so
 * the model and its clocks can be unit-tested off the game thread.
 */
public record BodyInputs(
		double health,        // 0..maxHealth
		double maxHealth,
		int foodLevel,        // 0..20
		float saturation,     // 0..20
		int airSupply,        // 0..maxAirSupply (300 at the surface)
		int maxAirSupply,
		boolean underWater,
		boolean sprinting,
		boolean moving,       // any locomotion this tick
		boolean falling,
		double threat,        // 0..1, nearby-hostile density
		int light,            // 0..15 raw brightness at the player's block
		int depth             // blocks below the surface, >= 0
) {

	/** A fully rested player on the surface at noon. */
	public static BodyInputs resting() {
		return new BodyInputs(20, 20, 20, 5f, 300, 300, false, false, false, false, 0.0, 15, 0);
	}
}
