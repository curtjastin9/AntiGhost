package com.litantighost;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny settings holder, saved to config/litantighost.properties. */
public final class AntiGhostConfig {

	public static boolean enabled = true;
	/** Extra ticks added to the confirm wait before an unconfirmed change is rolled back. */
	public static int extraTicks = 0;

	private AntiGhostConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("litantighost.properties");
	}

	public static void load() {
		Path path = file();
		if (!Files.exists(path)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			Properties props = new Properties();
			props.load(reader);
			enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
			extraTicks = Math.max(0, Math.min(40, Integer.parseInt(props.getProperty("extraTicks", "0").trim())));
		} catch (IOException | NumberFormatException e) {
			enabled = true;
			extraTicks = 0;
		}
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("enabled", Boolean.toString(enabled));
		props.setProperty("extraTicks", Integer.toString(extraTicks));
		try (Writer writer = Files.newBufferedWriter(file())) {
			props.store(writer, "LitAntiGhost. extraTicks: more = waits longer before rolling back (raise on laggy servers)");
		} catch (IOException ignored) {
		}
	}
}
