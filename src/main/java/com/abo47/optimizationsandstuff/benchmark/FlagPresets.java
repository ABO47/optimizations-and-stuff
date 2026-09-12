package com.abo47.optimizationsandstuff.benchmark;

import com.abo47.optimizationsandstuff.config.ModSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Named matrices for unattended /opt test runs, plus runtime
 * single-flag toggling for /opt flag.
 */
public final class FlagPresets {

    public static final class Preset {
        public final String name;
        public final String description;
        private final Consumer<ModSettings> apply;

        Preset(final String name, final String description, final Consumer<ModSettings> apply) {
            this.name = name;
            this.description = description;
            this.apply = apply;
        }

        void applyTo(@Nonnull final ModSettings settings) {
            apply.accept(settings);
        }
    }

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    private static void define(final String name, final String description,
            final Consumer<ModSettings> apply) {
        PRESETS.put(name, new Preset(name, description, apply));
    }

    static {
        define("off", "everything off (baseline)", FlagPresets::allOff);
        define("fetch", "AsyncFetch only", s -> s.asyncFetch = true);
        define("budget", "SpawnBudget only", s -> s.spawnBudget = true);
        define("resolve", "ChunkResolve only", s -> s.chunkResolve = true);
        define("sampling", "HeapSampling only", s -> s.heapSampling = true);
        define("dormancy", "ViewDormancy only", s -> s.viewDormancy = true);
        define("edits", "BulkEdits + ReuseFlushBuffers", s -> {
            s.bulkEdits = true;
            s.reuseFlushBuffers = true;
        });
        define("quiet", "QuietLogging only", s -> s.quietLogging = true);
        define("memoryguard", "MemoryGuard + dormancy + edits + quiet", s -> {
            s.memoryGuard = true;
            s.viewDormancy = true;
            s.bulkEdits = true;
            s.reuseFlushBuffers = true;
            s.quietLogging = true;
        });
        define("merging", "ItemMerging only", s -> s.itemMerging = true);
        define("felling", "TreeFelling only", s -> s.treeFelling = true);
        define("gcguard", "GcStutterGuard only", s -> s.gcStutterGuard = true);
        define("far", "FarView only", s -> s.farView = true);
        define("all", "every toggle on", s -> {
            s.asyncFetch = true;
            s.spawnBudget = true;
            s.chunkResolve = true;
            s.heapSampling = true;
            s.viewDormancy = true;
            s.bulkEdits = true;
            s.reuseFlushBuffers = true;
            s.quietLogging = true;
            s.memoryGuard = true;
            s.itemMerging = true;
            s.treeFelling = true;
            s.gcStutterGuard = true;
            s.farView = true;
            s.joinBoost = true;
        });
    }

    private FlagPresets() {
    }

    @Nonnull
    public static List<String> names() {
        return new ArrayList<>(PRESETS.keySet());
    }

    @Nullable
    public static Preset get(@Nullable final String name) {
        if (name == null) {
            return null;
        }
        return PRESETS.get(name.toLowerCase());
    }

    public static void apply(@Nonnull final ModSettings settings, @Nonnull final Preset preset) {
        allOff(settings);
        preset.applyTo(settings);
    }

    public static void allOff(@Nonnull final ModSettings settings) {
        settings.asyncFetch = false;
        settings.spawnBudget = false;
        settings.chunkResolve = false;
        settings.heapSampling = false;
        settings.viewDormancy = false;
        settings.bulkEdits = false;
        settings.reuseFlushBuffers = false;
        settings.quietLogging = false;
        settings.memoryGuard = false;
        settings.itemMerging = false;
        settings.treeFelling = false;
        settings.gcStutterGuard = false;
        settings.farView = false;
        settings.joinBoost = false;
    }

    @Nonnull
    public static List<String> flags() {
        List<String> out = new ArrayList<>();
        out.add("AsyncFetch");
        out.add("SpawnBudget");
        out.add("ChunkResolve");
        out.add("HeapSampling");
        out.add("ViewDistance");
        out.add("BulkEdits");
        out.add("ReuseFlushBuffers");
        out.add("QuietLogging");
        out.add("LowMemoryMode");
        out.add("ItemMerging");
        out.add("TreeFelling");
        out.add("GcStutterGuard");
        out.add("FarView");
        out.add("JoinBoost");
        return out;
    }

    @Nullable
    public static String setFlag(@Nonnull final ModSettings settings, @Nullable final String name, final boolean on) {
        if (name == null) {
            return "missing flag name. Toggleable: " + String.join(" ", flags());
        }
        switch (name.toLowerCase()) {
            case "asyncfetch": settings.asyncFetch = on; break;
            case "spawnbudget": settings.spawnBudget = on; break;
            case "chunkresolve": settings.chunkResolve = on; break;
            case "heapsampling": settings.heapSampling = on; break;
            case "viewdistance": settings.viewDormancy = on; break;
            case "bulkedits": settings.bulkEdits = on; break;
            case "reuseflushbuffers": settings.reuseFlushBuffers = on; break;
            case "quietlogging": settings.quietLogging = on; break;
            case "lowmemorymode": settings.memoryGuard = on; break;
            case "itemmerging": settings.itemMerging = on; break;
            case "treefelling": settings.treeFelling = on; break;
            case "gcstutterguard": settings.gcStutterGuard = on; break;
            case "farview": settings.farView = on; break;
            case "joinboost": settings.joinBoost = on; break;
            default: return "unknown flag '" + name + "'. Toggleable: " + String.join(" ", flags());
        }
        return null;
    }
}
