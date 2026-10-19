package dev.lume.aorta.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Aorta's settings, loaded from {@code config/aorta.json}. Mirrors the suite's
 * config style: a record with a {@code DEFAULT}, Gson-backed, atomically written.
 *
 * <p>Two independent volume levels are kept, one for the heartbeat (lub/dub)
 * and one for breathing, so each can be balanced on its own.
 */
public record AortaConfig(boolean enabled, double heartbeatVolume, double breathingVolume) {

	public static final AortaConfig DEFAULT = new AortaConfig(true, 0.2, 0.2);

	/** Bumped when the meaning of a stored value changes; v2/v3 halved volume. */
	private static final int CONFIG_VERSION = 4;

	private static final Logger LOGGER = LoggerFactory.getLogger("aorta");

	private static AortaConfig loaded;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aorta.json");
	}

	/** The currently loaded config, loading the on-disk file first if needed. */
	public static AortaConfig get() {
		if (loaded == null) {
			load();
		}
		return loaded;
	}

	/** Load from the default path, writing the default file first if absent. */
	public static void load() {
		Path path = file();
		if (Files.notExists(path)) {
			DEFAULT.save(path);
			loaded = DEFAULT;
			return;
		}
		loaded = load(path);
	}

	public static void reload() {
		load();
	}

	/** Replace the active config and persist it. */
	public static void set(AortaConfig next) {
		loaded = next;
		next.save(file());
	}

	/** Replace the active config in memory without touching disk (live preview). */
	public static void apply(AortaConfig next) {
		loaded = next;
	}

	/** Persist the active config to the default path. */
	public void save() {
		save(file());
	}

	public AortaConfig withEnabled(boolean enabled) {
		return new AortaConfig(enabled, heartbeatVolume, breathingVolume);
	}

	/** Set both volume levels at once (kept for backwards compatibility). */
	public AortaConfig withVolume(double volume) {
		double v = clamp(volume, 0.0, 1.0);
		return new AortaConfig(enabled, v, v);
	}

	public AortaConfig withHeartbeatVolume(double volume) {
		return new AortaConfig(enabled, clamp(volume, 0.0, 1.0), breathingVolume);
	}

	public AortaConfig withBreathingVolume(double volume) {
		return new AortaConfig(enabled, heartbeatVolume, clamp(volume, 0.0, 1.0));
	}

	// Aliases for the shorter naming used elsewhere.
	public AortaConfig withHeartVolume(double volume) {
		return withHeartbeatVolume(volume);
	}

	public AortaConfig withBreathVolume(double volume) {
		return withBreathingVolume(volume);
	}

	/** Overall volume: the heartbeat level, used as the legacy single value. */
	public double volume() {
		return heartbeatVolume;
	}

	public double heartVolume() {
		return heartbeatVolume;
	}

	public double breathVolume() {
		return breathingVolume;
	}

	// Backwards-compatible constructors.
	public AortaConfig(boolean enabled, double volume) {
		this(enabled, volume, volume);
	}

	public AortaConfig(boolean enabled, double volume, double heartbeatVolume, double breathingVolume) {
		this(enabled, heartbeatVolume, breathingVolume);
	}

	/** Parse a config file, falling back to {@link #DEFAULT} on any problem. */
	static AortaConfig load(Path path) {
		try {
			String raw = Files.readString(path, StandardCharsets.UTF_8);
			JsonElement element = JsonParser.parseString(raw);
			if (element.isJsonObject()) {
				return fromJson(element.getAsJsonObject());
			}
		} catch (NoSuchFileException e) {
			LOGGER.warn("aorta.json is missing, using defaults");
		} catch (IOException e) {
			LOGGER.warn("failed to read aorta.json: {}", e.getMessage());
		} catch (JsonParseException e) {
			LOGGER.warn("aorta.json is malformed, using defaults: {}", e.getMessage());
		}
		return DEFAULT;
	}

	/** Write this config to {@code path} atomically (tmp file + rename). */
	public void save(Path path) {
		try {
			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, toJson(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("failed to save aorta.json: {}", e.getMessage());
		}
	}

	static AortaConfig fromJson(JsonObject json) {
		int version = integer(json, "version", 1);
		boolean enabled = flag(json, "enabled", DEFAULT.enabled());

		double heartbeatVolume;
		double breathingVolume;

		// New per-sound keys take precedence when present.
		boolean hasHeart = hasAny(json, "heartbeatVolume", "heartVolume", "heartbeat_volume", "heart_volume");
		boolean hasBreath = hasAny(json, "breathingVolume", "breathVolume", "breathing_volume", "breath_volume");

		if (hasHeart || hasBreath) {
			heartbeatVolume = hasHeart
					? numAny(json, DEFAULT.heartbeatVolume(), "heartbeatVolume", "heartVolume", "heartbeat_volume", "heart_volume")
					: DEFAULT.heartbeatVolume();
			breathingVolume = hasBreath
					? numAny(json, DEFAULT.breathingVolume(), "breathingVolume", "breathVolume", "breathing_volume", "breath_volume")
					: DEFAULT.breathingVolume();
			// If only one side was given, mirror the legacy single volume for the other.
			if (!hasHeart && hasBreath) {
				heartbeatVolume = num(json, "volume", DEFAULT.heartbeatVolume());
			}
			if (!hasBreath && hasHeart) {
				breathingVolume = num(json, "volume", DEFAULT.breathingVolume());
			}
		} else if (version >= CONFIG_VERSION) {
			heartbeatVolume = num(json, "heartbeatVolume", DEFAULT.heartbeatVolume());
			breathingVolume = num(json, "breathingVolume", DEFAULT.breathingVolume());
		} else {
			// Legacy single volume: rescale by that version's default so a hand-tuned
			// level keeps its relative intent instead of staying loud.
			double oldDefault = version == 1 ? 0.8 : version == 2 ? 0.4 : 0.2;
			double old = num(json, "volume", oldDefault);
			double migrated = old * (DEFAULT.heartbeatVolume() / oldDefault);
			migrated = clamp(migrated, 0.0, 1.0);
			heartbeatVolume = migrated;
			breathingVolume = migrated;
		}

		return new AortaConfig(enabled, clamp(heartbeatVolume, 0.0, 1.0), clamp(breathingVolume, 0.0, 1.0));
	}

	String toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("version", CONFIG_VERSION);
		json.addProperty("enabled", enabled);
		json.addProperty("heartbeatVolume", heartbeatVolume);
		json.addProperty("breathingVolume", breathingVolume);
		return new GsonBuilder().setPrettyPrinting().create().toJson(json);
	}

	private static boolean hasAny(JsonObject json, String... keys) {
		for (String k : keys) {
			if (json.has(k)) {
				return true;
			}
		}
		return false;
	}

	private static double numAny(JsonObject json, double fallback, String... keys) {
		for (String k : keys) {
			JsonElement e = json.get(k);
			if (e instanceof JsonPrimitive p && p.isNumber()) {
				return p.getAsDouble();
			}
		}
		return fallback;
	}

	private static double num(JsonObject json, String key, double fallback) {
		JsonElement e = json.get(key);
		return e instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : fallback;
	}

	private static boolean flag(JsonObject json, String key, boolean fallback) {
		JsonElement e = json.get(key);
		return e instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : fallback;
	}

	private static int integer(JsonObject json, String key, int fallback) {
		JsonElement e = json.get(key);
		return e instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : fallback;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
