package dev.lume.aorta.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AortaConfigTest {

	@TempDir
	Path tmp;

	@Test
	void roundTripPreservesValues() throws Exception {
		AortaConfig c = new AortaConfig(false, 0.42);
		Path p = tmp.resolve("aorta.json");
		c.save(p);
		AortaConfig loaded = AortaConfig.load(p);
		assertEquals(false, loaded.enabled());
		assertEquals(0.42, loaded.volume(), 1e-9);
	}

	@Test
	void malformedJsonFallsBackToDefaults() throws Exception {
		Path p = tmp.resolve("bad.json");
		Files.writeString(p, "{ this is not json");
		AortaConfig loaded = AortaConfig.load(p);
		assertEquals(AortaConfig.DEFAULT.enabled(), loaded.enabled());
		assertEquals(AortaConfig.DEFAULT.volume(), loaded.volume(), 1e-9);
	}

	@Test
	void missingKeysFallBackToDefaults() throws Exception {
		Path p = tmp.resolve("empty.json");
		Files.writeString(p, "{}");
		AortaConfig loaded = AortaConfig.load(p);
		assertEquals(AortaConfig.DEFAULT.enabled(), loaded.enabled());
		assertEquals(AortaConfig.DEFAULT.volume(), loaded.volume(), 1e-9);
	}
}
