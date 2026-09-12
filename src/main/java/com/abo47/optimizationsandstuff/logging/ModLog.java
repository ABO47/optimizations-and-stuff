package com.abo47.optimizationsandstuff.logging;

import com.abo47.optimizationsandstuff.OptimizationsPlugin;
import com.abo47.optimizationsandstuff.config.SettingsLoader;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Single choke point for mod logging. While quiet mode is on, routine info
 * is suppressed and counted; warnings and errors always pass through.
 */
public final class ModLog {

    private static volatile boolean quiet;
    private static long emitted;
    private static long suppressed;

    private ModLog() {
    }

    public static void refresh() {
        var current = SettingsLoader.current();
        quiet = (current.quietLogging != null && current.quietLogging)
            || (current.memoryGuard != null && current.memoryGuard);
    }

    public static void info(@Nonnull final String message) {
        if (quiet) {
            suppressed++;
            return;
        }
        emitted++;
        var plugin = OptimizationsPlugin.get();
        if (plugin != null) {
            plugin.getLogger().at(Level.INFO).log(message);
        }
    }

    public static void warning(@Nonnull final String message) {
        emitted++;
        var plugin = OptimizationsPlugin.get();
        if (plugin != null) {
            plugin.getLogger().at(Level.WARNING).log(message);
        }
    }

    public static long getEmitted() {
        return emitted;
    }

    public static long getSuppressed() {
        return suppressed;
    }

    public static void resetCounters() {
        emitted = 0;
        suppressed = 0;
    }
}
