package com.abo47.optimizationsandstuff.memory;

import com.abo47.optimizationsandstuff.logging.ModLog;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Engine-constant patches. Only mutable statics can be rewritten from a mod;
 * static final primitives are inlined at compile time, so those are logged as
 * fork sketches. Everything enforceable from a mod lives in the systems.
 */
public final class EngineConstantPatches {

    private EngineConstantPatches() {
    }

    public static void apply() {
        // LOD cull ratio is a mutable static double -> 10x, culls 50%+ far entities.
        setDouble(
            "com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems$LODCull",
            "ENTITY_LOD_RATIO", 0.00035);

        // Final constants (inlined) - attempt + log fork sketch.
        tryFinalInt("com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker",
            "MAX_SECTIONS_PER_TICK", 8, "ChunkTracker.<init> copies it to maxSectionsPerTick per player");
        tryFinalInt("com.hypixel.hytale.server.core.universe.world.WorldMapTracker",
            "MAX_IMAGE_GENERATION", 2, "per-player map image budget");
        tryFinalInt("com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk",
            "KEEP_ALIVE_DEFAULT", 2, "15 -> 2, new columns/ChunkSections only");
        tryFinalInt("com.hypixel.hytale.server.spawning.controllers.SpawnJobSystem",
            "JOB_BUDGET", 8, "64 -> 8 columns/job per tick");
        tryFinalInt("com.hypixel.hytale.server.spawning.world.system.WorldSpawnJobSystems",
            "JOB_BUDGET", 8, "64 -> 8, second spawn pipeline");

        clearThreadLocal("com.hypixel.hytale.codec.util.RawJsonReader", "READ_BUFFER");

        ModLog.warning("MemoryGuard JVM: use -Xmx cap + -XX:+UseZGC, RocksDB block cache 128MiB -> 16MiB fork, "
            + "BLOB_CACHE stays 0, IndexedStorage mmap off for lowest RSS. "
            + "PacketIO <1KB uncompressed threshold + CachedPacket send-and-close need a fork.");
    }

    private static void setDouble(final String className, final String field, final double value) {
        try {
            Class<?> type = Class.forName(className);
            Field f = type.getDeclaredField(field);
            f.setAccessible(true);
            if (Modifier.isFinal(f.getModifiers())) {
                ModLog.warning("EngineConstantPatches: " + className + "." + field + " is final, fork needed");
                return;
            }
            f.setDouble(null, value);
            ModLog.warning("EngineConstantPatches: " + field + " = " + value);
        } catch (Exception e) {
            ModLog.warning("EngineConstantPatches: cannot set " + className + "." + field + ": " + e.getMessage());
        }
    }

    private static void tryFinalInt(final String className, final String field, final int wanted,
            final String note) {
        try {
            Class<?> type = Class.forName(className);
            Field f = type.getDeclaredField(field);
            f.setAccessible(true);
            Object before = f.get(null);
            ModLog.warning("EngineConstantPatches: " + className + "." + field + " = " + before
                + " (final, want " + wanted + "; fork: " + note + ")");
        } catch (Exception e) {
            ModLog.warning("EngineConstantPatches: " + className + "." + field + " missing: " + e.getMessage());
        }
    }

    private static void clearThreadLocal(final String className, final String field) {
        try {
            Class<?> type = Class.forName(className);
            Field f = type.getDeclaredField(field);
            f.setAccessible(true);
            Object tl = f.get(null);
            if (tl instanceof ThreadLocal) {
                ((ThreadLocal<?>) tl).remove();
                ModLog.warning("EngineConstantPatches: cleared " + className + "." + field + " for this thread");
            }
        } catch (Exception e) {
            ModLog.warning("EngineConstantPatches: cannot clear " + className + "." + field + ": " + e.getMessage());
        }
    }
}
