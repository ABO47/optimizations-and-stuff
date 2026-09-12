package com.abo47.optimizationsandstuff.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches the settings file. Callers only touch the in-memory cache,
 * never disk on the tick path. Each toggle nests with its own numbers:
 *
 * <pre>
 * {
 *   "viewDormancy": { "enabled": false, "minViewDistance": 8, ... },
 *   "memoryGuard": { "enabled": false, "viewDistance": 4, ... },
 *   ...
 * }
 * </pre>
 */
public final class SettingsLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Flat field name -> {group, key}. Group "" means a top-level scalar. */
    private static final List<String[]> KEYS = new ArrayList<>();

    private static void key(final String field, final String group, final String nested) {
        KEYS.add(new String[]{field, group, nested});
    }

    static {
        key("QuietLogging", "quietLogging", "enabled");
        key("GcStutterGuard", "gcStutterGuard", "enabled");
        key("AsyncFetch", "asyncFetch", "enabled");
        key("SpawnBudget", "spawnBudget", "enabled");
        key("SpawnBudgetPerTick", "spawnBudget", "maxPerTick");
        key("ChunkResolve", "chunkResolve", "enabled");
        key("MaxResolvesPerTick", "chunkResolve", "maxPerTick");
        key("HeapSampling", "heapSampling", "enabled");
        key("ViewDormancy", "viewDistance", "enabled");
        key("MinViewDistance", "viewDistance", "min");
        key("MaxViewDistance", "viewDistance", "max");
        key("DormantMarginChunks", "viewDistance", "dormantMargin");
        key("DormancyIntervalTicks", "viewDistance", "scanInterval");
        key("BulkEdits", "bulkEdits", "enabled");
        key("MaxBulkEditsPerTick", "bulkEdits", "maxPerTick");
        key("ReuseFlushBuffers", "reuseFlushBuffers", "enabled");
        key("MemoryGuard", "lowMemoryMode", "enabled");
        key("GuardViewDistance", "lowMemoryMode", "maxView");
        key("GuardMapRadius", "lowMemoryMode", "mapView");
        key("EntityHardCap", "lowMemoryMode", "entityCap");
        key("ItemMerging", "itemMerging", "enabled");
        key("ItemMergingRadius", "itemMerging", "radius");
        key("ItemMergingIntervalTicks", "itemMerging", "everyTicks");
        key("TreeFelling", "treeFelling", "enabled");
        key("TreeMaxBlocks", "treeFelling", "maxBlocks");
        key("FarView", "farView", "enabled");
        key("EntityViewDistance", "farView", "entityDistance");
        key("FarHotRadius", "farView", "tickRadius");
        key("JoinBoost", "chunkStreaming.joinBoost", "enabled");
        key("JoinBoostPerSecond", "chunkStreaming.joinBoost", "perSecond");
        key("JoinBoostPerTick", "chunkStreaming.joinBoost", "perTick");
        key("MaxStreamPerTick", "chunkStreaming", "maxPerTick");
    }

    private static ModSettings cached;

    private SettingsLoader() {
    }

    public static synchronized void init() {
        cached = readOrGenerate(ConfigPaths.settingsFile());
    }

    @Nonnull
    public static synchronized ModSettings current() {
        if (cached == null) {
            cached = readOrGenerate(ConfigPaths.settingsFile());
        }
        return cached;
    }

    public static synchronized void reloadAll() {
        cached = null;
    }

    public static synchronized void save(@Nonnull final ModSettings settings) {
        Path file = ConfigPaths.settingsFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(toNested(settings)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModLog.warning("Failed to save " + file + ": " + e.getMessage());
        }
        cached = settings;
        ModLog.refresh();
    }

    @Nonnull
    public static synchronized ModSettings snapshot() {
        return GSON.fromJson(GSON.toJson(current()), ModSettings.class);
    }

    @Nonnull
    private static ModSettings readOrGenerate(@Nonnull final Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ignored) {
        }
        if (!Files.isRegularFile(file)) {
            ModSettings defaults = ModSettings.withDefaults();
            saveToFile(file, defaults);
            return defaults;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(text, JsonObject.class);
            if (root == null) {
                return new ModSettings();
            }
            ModSettings parsed = GSON.fromJson(flatten(root), ModSettings.class);
            return parsed != null ? parsed : new ModSettings();
        } catch (Exception e) {
            ModLog.warning("Failed to parse " + file + ", falling through to defaults: " + e.getMessage());
            return new ModSettings();
        }
    }

    @Nonnull
    private static JsonObject flatten(@Nonnull final JsonObject root) {
        JsonObject flat = new JsonObject();
        for (String[] row : KEYS) {
            JsonElement found = lookup(root, row[1] + "." + row[2]);
            if (found != null) {
                flat.add(row[0], found);
            }
        }
        return flat;
    }

    @Nullable
    private static JsonElement lookup(@Nonnull final JsonObject root, @Nonnull final String path) {
        String[] parts = path.split("\\.");
        JsonElement node = root;
        for (String part : parts) {
            if (node == null || !node.isJsonObject()) {
                return null;
            }
            node = node.getAsJsonObject().get(part);
        }
        return node;
    }

    @Nonnull
    private static JsonObject toNested(@Nonnull final ModSettings settings) {
        JsonObject flat = GSON.toJsonTree(settings).getAsJsonObject();
        JsonObject root = new JsonObject();
        List<String> placed = new ArrayList<>();
        List<String> groupOrder = new ArrayList<>();
        for (String[] row : KEYS) {
            String top = row[1].contains(".") ? row[1].substring(0, row[1].indexOf('.')) : row[1];
            if (!groupOrder.contains(top)) {
                groupOrder.add(top);
            }
        }
        for (String top : groupOrder) {
            JsonObject topObj = new JsonObject();
            List<String> subOrder = new ArrayList<>();
            for (String[] row : KEYS) {
                String[] target = row[1].split("\\.");
                if (target.length == 2 && target[0].equals(top) && !subOrder.contains(target[1])) {
                    subOrder.add(target[1]);
                }
            }
            for (String[] row : KEYS) {
                String[] target = row[1].split("\\.");
                if (target.length == 1 && target[0].equals(top) && flat.has(row[0])) {
                    topObj.add(row[2], flat.get(row[0]));
                    placed.add(row[0]);
                }
            }
            for (String sub : subOrder) {
                JsonObject subObj = new JsonObject();
                for (String[] row : KEYS) {
                    String[] target = row[1].split("\\.");
                    if (target.length == 2 && target[0].equals(top) && target[1].equals(sub)
                            && flat.has(row[0])) {
                        subObj.add(row[2], flat.get(row[0]));
                        placed.add(row[0]);
                    }
                }
                topObj.add(sub, subObj);
            }
            root.add(top, topObj);
        }
        for (Map.Entry<String, JsonElement> entry : flat.entrySet()) {
            if (!placed.contains(entry.getKey())) {
                root.add(entry.getKey(), entry.getValue());
            }
        }
        return root;
    }

    private static void saveToFile(@Nonnull final Path file, @Nonnull final ModSettings settings) {
        try {
            Files.writeString(file, GSON.toJson(toNested(settings)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModLog.warning("Failed to write default " + file + ": " + e.getMessage());
        }
    }
}
