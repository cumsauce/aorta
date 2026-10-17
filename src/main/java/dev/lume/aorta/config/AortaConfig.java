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
 */
public record AortaConfig(boolean enabled, double volume) {

	public static final AortaConfig DEFAULT = new AortaConfig(true, 0.2);

	/** Bumped when the meaning of a stored value changes; v2 and v3 halved volume. */
	private static final int CONFIG_VERSION = 3;

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
		return new AortaConfig(enabled, volume);
	}

	public AortaConfig withVolume(double volume) {
		return new AortaConfig(enabled, clamp(volume, 0.0, 1.0));
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
		double volume;
		if (version >= CONFIG_VERSION) {
			volume = num(json, "volume", DEFAULT.volume());
		} else {
			// v1 defaulted to 0.8 and v2 to 0.4; each revision halved the level.
			// Rescale by that version's default so a hand-tuned level keeps its
			// relative intent instead of staying loud.
			double oldDefault = version == 1 ? 0.8 : 0.4;
			double old = num(json, "volume", oldDefault);
			volume = old * (DEFAULT.volume() / oldDefault);
		}
		return new AortaConfig(enabled, clamp(volume, 0.0, 1.0));
	}

	String toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("version", CONFIG_VERSION);
		json.addProperty("enabled", enabled);
		json.addProperty("volume", volume);
		return new GsonBuilder().setPrettyPrinting().create().toJson(json);
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
