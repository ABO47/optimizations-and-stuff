package com.abo47.optimizationsandstuff.config;

import com.abo47.optimizationsandstuff.OptimizationsPlugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ConfigPaths {

    private ConfigPaths() {
    }

    @Nonnull
    public static Path configRoot() {
        var plugin = OptimizationsPlugin.get();
        if (plugin != null && plugin.getDataDirectory() != null) {
            Path dataDir = plugin.getDataDirectory();
            Path configDir = dataDir.getParent();
            if (configDir != null) {
                Path modsDir = configDir.getParent();
                if (modsDir != null) {
                    return modsDir.resolve("optimizationsandstuff");
                }
                return configDir.resolve("optimizationsandstuff");
            }
        }
        return Paths.get("optimizationsandstuff");
    }

    @Nonnull
    public static Path settingsFile() {
        return configRoot().resolve("settings.json");
    }

    @Nonnull
    public static Path logsDirectory() {
        return configRoot().resolve("logs");
    }

    @Nonnull
    public static String sanitizeFileName(@Nonnull final String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "run";
        }
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
            out.append(ok ? c : '_');
        }
        return out.toString();
    }
}
