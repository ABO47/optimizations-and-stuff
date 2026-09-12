package com.abo47.optimizationsandstuff.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Resolved tuning: file settings overlaid on bundled asset defaults, exposed
 * per tick. Systems call {@link #resolve(World)} and read plain getters.
 */
public class TuningConfig {

    public static final String ID = "OptimizationsAndStuff_Tuning";

    public static final BuilderCodec<TuningConfig> CODEC = BuilderCodec.builder(TuningConfig.class, TuningConfig::new)
        .append(
            new KeyedCodec<>("AsyncFetch", Codec.BOOLEAN),
            (o, v) -> o.asyncFetch = v,
            o -> o.asyncFetch
        )
        .documentation("Use async chunk fetch paths, never blocking calls on tick threads.")
        .add()
        .append(
            new KeyedCodec<>("SpawnBudget", Codec.BOOLEAN),
            (o, v) -> o.spawnBudget = v,
            o -> o.spawnBudget
        )
        .documentation("Cap mod-driven entity spawns per tick.")
        .add()
        .append(
            new KeyedCodec<>("SpawnBudgetPerTick", Codec.INTEGER),
            (o, v) -> o.spawnBudgetPerTick = v,
            o -> o.spawnBudgetPerTick
        )
        .documentation("Max mod-driven spawns per tick.")
        .add()
        .append(
            new KeyedCodec<>("ChunkResolve", Codec.BOOLEAN),
            (o, v) -> o.chunkResolve = v,
            o -> o.chunkResolve
        )
        .documentation("Pace chunk reference resolves through a bounded queue.")
        .add()
        .append(
            new KeyedCodec<>("MaxResolvesPerTick", Codec.INTEGER),
            (o, v) -> o.maxResolvesPerTick = v,
            o -> o.maxResolvesPerTick
        )
        .documentation("Max chunk resolves per tick.")
        .add()
        .append(
            new KeyedCodec<>("HeapSampling", Codec.BOOLEAN),
            (o, v) -> o.heapSampling = v,
            o -> o.heapSampling
        )
        .documentation("Sample heap delta per tick into a bounded ring for leak hunting.")
        .add()
        .append(
            new KeyedCodec<>("ViewDormancy", Codec.BOOLEAN),
            (o, v) -> o.viewDormancy = v,
            o -> o.viewDormancy
        )
        .documentation("Clamp view distance into [min, max] and park far sections NonTicking.")
        .add()
        .append(
            new KeyedCodec<>("MinViewDistance", Codec.INTEGER),
            (o, v) -> o.minViewDistance = v,
            o -> o.minViewDistance
        )
        .documentation("Floor for the view clamp in chunks.")
        .add()
        .append(
            new KeyedCodec<>("MaxViewDistance", Codec.INTEGER),
            (o, v) -> o.maxViewDistance = v,
            o -> o.maxViewDistance
        )
        .documentation("Ceiling for the view clamp in chunks.")
        .add()
        .append(
            new KeyedCodec<>("DormantMarginChunks", Codec.INTEGER),
            (o, v) -> o.dormantMarginChunks = v,
            o -> o.dormantMarginChunks
        )
        .documentation("Extra chunks beyond max view before sections go dormant.")
        .add()
        .append(
            new KeyedCodec<>("DormancyIntervalTicks", Codec.INTEGER),
            (o, v) -> o.dormancyIntervalTicks = v,
            o -> o.dormancyIntervalTicks
        )
        .documentation("Ticks between dormancy scans.")
        .add()
        .append(
            new KeyedCodec<>("BulkEdits", Codec.BOOLEAN),
            (o, v) -> o.bulkEdits = v,
            o -> o.bulkEdits
        )
        .documentation("Buffer mod-driven block edits, flush sorted by section.")
        .add()
        .append(
            new KeyedCodec<>("MaxBulkEditsPerTick", Codec.INTEGER),
            (o, v) -> o.maxBulkEditsPerTick = v,
            o -> o.maxBulkEditsPerTick
        )
        .documentation("Max buffered edits flushed per tick.")
        .add()
        .append(
            new KeyedCodec<>("ReuseFlushBuffers", Codec.BOOLEAN),
            (o, v) -> o.reuseFlushBuffers = v,
            o -> o.reuseFlushBuffers
        )
        .documentation("Borrow flush buffers from a bounded pool instead of allocating.")
        .add()
        .append(
            new KeyedCodec<>("QuietLogging", Codec.BOOLEAN),
            (o, v) -> o.quietLogging = v,
            o -> o.quietLogging
        )
        .documentation("Suppress routine mod info logs, keep warnings and errors.")
        .add()
        .append(
            new KeyedCodec<>("MemoryGuard", Codec.BOOLEAN),
            (o, v) -> o.memoryGuard = v,
            o -> o.memoryGuard
        )
        .documentation("Hard view/map caps, entity hard cap, heap-threshold GC hints.")
        .add()
        .append(
            new KeyedCodec<>("GuardViewDistance", Codec.INTEGER),
            (o, v) -> o.guardViewDistance = v,
            o -> o.guardViewDistance
        )
        .documentation("View clamp in chunks while MemoryGuard is on.")
        .add()
        .append(
            new KeyedCodec<>("GuardMapRadius", Codec.INTEGER),
            (o, v) -> o.guardMapRadius = v,
            o -> o.guardMapRadius
        )
        .documentation("World-map radius override while MemoryGuard is on.")
        .add()
        .append(
            new KeyedCodec<>("EntityHardCap", Codec.INTEGER),
            (o, v) -> o.entityHardCap = v,
            o -> o.entityHardCap
        )
        .documentation("Global non-player entity cap; excess farthest despawned.")
        .add()
        .append(
            new KeyedCodec<>("ItemMerging", Codec.BOOLEAN),
            (o, v) -> o.itemMerging = v,
            o -> o.itemMerging
        )
        .documentation("Merge nearby dropped-item entities into stacked survivors.")
        .add()
        .append(
            new KeyedCodec<>("ItemMergingRadius", Codec.INTEGER),
            (o, v) -> o.itemMergingRadius = v,
            o -> o.itemMergingRadius
        )
        .documentation("Merge radius in blocks (engine default is 2).")
        .add()
        .append(
            new KeyedCodec<>("ItemMergingIntervalTicks", Codec.INTEGER),
            (o, v) -> o.itemMergingIntervalTicks = v,
            o -> o.itemMergingIntervalTicks
        )
        .documentation("Ticks between merge scans.")
        .add()
        .append(
            new KeyedCodec<>("ItemMergingMaxLifetime", Codec.INTEGER),
            (o, v) -> o.itemMergingMaxLifetime = v,
            o -> o.itemMergingMaxLifetime
        )
        .documentation("Cap dropped-item lifetime in seconds, 0 = engine default.")
        .add()
        .append(
            new KeyedCodec<>("TreeFelling", Codec.BOOLEAN),
            (o, v) -> o.treeFelling = v,
            o -> o.treeFelling
        )
        .documentation("Fell whole trunks as toppling falling blocks with watched landings.")
        .add()
        .append(
            new KeyedCodec<>("TreeMaxBlocks", Codec.INTEGER),
            (o, v) -> o.treeMaxBlocks = v,
            o -> o.treeMaxBlocks
        )
        .documentation("Max connected logs/leaves per felling.")
        .add()
        .append(
            new KeyedCodec<>("GcStutterGuard", Codec.BOOLEAN),
            (o, v) -> o.gcStutterGuard = v,
            o -> o.gcStutterGuard
        )
        .documentation("Worst-tick tracking plus heap-threshold GC hints.")
        .add()
        .append(
            new KeyedCodec<>("FarView", Codec.BOOLEAN),
            (o, v) -> o.farView = v,
            o -> o.farView
        )
        .documentation("Big chunk radius with a small decoupled entity radius.")
        .add()
        .append(
            new KeyedCodec<>("EntityViewDistance", Codec.INTEGER),
            (o, v) -> o.entityViewDistance = v,
            o -> o.entityViewDistance
        )
        .documentation("Entity tracking radius in chunks while FarView is on.")
        .add()
        .append(
            new KeyedCodec<>("FarHotRadius", Codec.INTEGER),
            (o, v) -> o.farHotRadius = v,
            o -> o.farHotRadius
        )
        .documentation("Block-ticking hot radius while FarView is on.")
        .add()
        .append(
            new KeyedCodec<>("JoinBoost", Codec.BOOLEAN),
            (o, v) -> o.joinBoost = v,
            o -> o.joinBoost
        )
        .documentation("Raise chunk send budgets so joins fill faster.")
        .add()
        .append(
            new KeyedCodec<>("JoinBoostPerSecond", Codec.INTEGER),
            (o, v) -> o.joinBoostPerSecond = v,
            o -> o.joinBoostPerSecond
        )
        .documentation("Sections per second while JoinBoost is on.")
        .add()
        .append(
            new KeyedCodec<>("JoinBoostPerTick", Codec.INTEGER),
            (o, v) -> o.joinBoostPerTick = v,
            o -> o.joinBoostPerTick
        )
        .documentation("Sections per tick while JoinBoost is on.")
        .add()
        .append(
            new KeyedCodec<>("MaxStreamPerTick", Codec.INTEGER),
            (o, v) -> o.maxStreamPerTick = v,
            o -> o.maxStreamPerTick
        )
        .documentation("Per-tick section cap while streaming, 0 = engine default.")
        .add()
        .build();

    protected boolean asyncFetch = true;
    protected boolean spawnBudget = true;
    protected int spawnBudgetPerTick = 8;
    protected boolean chunkResolve = true;
    protected int maxResolvesPerTick = 64;
    protected boolean heapSampling = true;
    protected boolean viewDormancy = true;
    protected int minViewDistance = 8;
    protected int maxViewDistance = 8;
    protected int dormantMarginChunks = 2;
    protected int dormancyIntervalTicks = 40;
    protected boolean bulkEdits = true;
    protected int maxBulkEditsPerTick = 256;
    protected boolean reuseFlushBuffers = true;
    protected boolean quietLogging = true;
    protected boolean memoryGuard = true;
    protected int guardViewDistance = 4;
    protected int guardMapRadius = 2;
    protected int entityHardCap = 1500;
    protected boolean itemMerging = true;
    protected int itemMergingRadius = 8;
    protected int itemMergingIntervalTicks = 20;
    protected int itemMergingMaxLifetime = 60;
    protected boolean treeFelling = true;
    protected int treeMaxBlocks = 192;
    protected boolean gcStutterGuard = true;
    protected boolean farView = true;
    protected int entityViewDistance = 4;
    protected int farHotRadius = 6;
    protected boolean joinBoost = true;
    protected int joinBoostPerSecond = 1280;
    protected int joinBoostPerTick = 80;
    protected int maxStreamPerTick = 24;

    @Nonnull
    public static TuningConfig resolve(@Nonnull final World world) {
        ModSettings file = SettingsLoader.current();
        TuningConfig asset = null;
        try {
            var pluginConfig = world.getGameplayConfig().getPluginConfig();
            if (pluginConfig != null) {
                Object found = pluginConfig.get(TuningConfig.class);
                if (found instanceof TuningConfig) {
                    asset = (TuningConfig) found;
                }
            }
        } catch (Exception ignored) {
        }
        TuningConfig resolved = new TuningConfig();
        resolved.asyncFetch = pick(file.asyncFetch, asset != null ? asset.asyncFetch : null, true);
        resolved.spawnBudget = pick(file.spawnBudget, asset != null ? asset.spawnBudget : null, true);
        resolved.spawnBudgetPerTick = pick(file.spawnBudgetPerTick, asset != null ? asset.spawnBudgetPerTick : null, 8);
        resolved.chunkResolve = pick(file.chunkResolve, asset != null ? asset.chunkResolve : null, true);
        resolved.maxResolvesPerTick = pick(file.maxResolvesPerTick, asset != null ? asset.maxResolvesPerTick : null, 64);
        resolved.heapSampling = pick(file.heapSampling, asset != null ? asset.heapSampling : null, true);
        resolved.viewDormancy = pick(file.viewDormancy, asset != null ? asset.viewDormancy : null, true);
        resolved.minViewDistance = pick(file.minViewDistance, asset != null ? asset.minViewDistance : null, 8);
        resolved.maxViewDistance = pick(file.maxViewDistance, asset != null ? asset.maxViewDistance : null, 8);
        resolved.dormantMarginChunks = pick(file.dormantMarginChunks, asset != null ? asset.dormantMarginChunks : null, 2);
        resolved.dormancyIntervalTicks = pick(file.dormancyIntervalTicks, asset != null ? asset.dormancyIntervalTicks : null, 40);
        resolved.bulkEdits = pick(file.bulkEdits, asset != null ? asset.bulkEdits : null, true);
        resolved.maxBulkEditsPerTick = pick(file.maxBulkEditsPerTick, asset != null ? asset.maxBulkEditsPerTick : null, 256);
        resolved.reuseFlushBuffers = pick(file.reuseFlushBuffers, asset != null ? asset.reuseFlushBuffers : null, true);
        resolved.quietLogging = pick(file.quietLogging, asset != null ? asset.quietLogging : null, true);
        resolved.memoryGuard = pick(file.memoryGuard, asset != null ? asset.memoryGuard : null, true);
        resolved.guardViewDistance = pick(file.guardViewDistance, asset != null ? asset.guardViewDistance : null, 4);
        resolved.guardMapRadius = pick(file.guardMapRadius, asset != null ? asset.guardMapRadius : null, 2);
        resolved.entityHardCap = pick(file.entityHardCap, asset != null ? asset.entityHardCap : null, 1500);
        resolved.itemMerging = pick(file.itemMerging, asset != null ? asset.itemMerging : null, true);
        resolved.itemMergingRadius = pick(file.itemMergingRadius, asset != null ? asset.itemMergingRadius : null, 8);
        resolved.itemMergingIntervalTicks = pick(file.itemMergingIntervalTicks, asset != null ? asset.itemMergingIntervalTicks : null, 20);
        resolved.itemMergingMaxLifetime = pick(file.itemMergingMaxLifetime, asset != null ? asset.itemMergingMaxLifetime : null, 60);
        resolved.treeFelling = pick(file.treeFelling, asset != null ? asset.treeFelling : null, true);
        resolved.treeMaxBlocks = pick(file.treeMaxBlocks, asset != null ? asset.treeMaxBlocks : null, 192);
        resolved.gcStutterGuard = pick(file.gcStutterGuard, asset != null ? asset.gcStutterGuard : null, true);
        resolved.farView = pick(file.farView, asset != null ? asset.farView : null, true);
        resolved.entityViewDistance = pick(file.entityViewDistance, asset != null ? asset.entityViewDistance : null, 4);
        resolved.farHotRadius = pick(file.farHotRadius, asset != null ? asset.farHotRadius : null, 6);
        resolved.joinBoost = pick(file.joinBoost, asset != null ? asset.joinBoost : null, true);
        resolved.joinBoostPerSecond = pick(file.joinBoostPerSecond, asset != null ? asset.joinBoostPerSecond : null, 1280);
        resolved.joinBoostPerTick = pick(file.joinBoostPerTick, asset != null ? asset.joinBoostPerTick : null, 80);
        resolved.maxStreamPerTick = pick(file.maxStreamPerTick, asset != null ? asset.maxStreamPerTick : null, 24);
        return resolved;
    }

    private static boolean pick(@Nullable final Boolean file, @Nullable final Boolean asset, final boolean def) {
        if (file != null) {
            return file;
        }
        if (asset != null) {
            return asset;
        }
        return def;
    }

    private static int pick(@Nullable final Integer file, @Nullable final Integer asset, final int def) {
        if (file != null) {
            return file;
        }
        if (asset != null) {
            return asset;
        }
        return def;
    }

    public boolean isAsyncFetch() {
        return asyncFetch;
    }

    public boolean isSpawnBudget() {
        return spawnBudget;
    }

    public int getSpawnBudgetPerTick() {
        return Math.max(0, spawnBudgetPerTick);
    }

    public boolean isChunkResolve() {
        return chunkResolve;
    }

    public int getMaxResolvesPerTick() {
        return Math.max(1, maxResolvesPerTick);
    }

    public boolean isHeapSampling() {
        return heapSampling;
    }

    public boolean isViewDormancy() {
        return viewDormancy;
    }

    public int getMinViewDistance() {
        return Math.max(2, minViewDistance);
    }

    public int getMaxViewDistance() {
        return Math.max(getMinViewDistance(), maxViewDistance);
    }

    public int getDormantMarginChunks() {
        return Math.max(0, dormantMarginChunks);
    }

    public int getDormancyIntervalTicks() {
        return Math.max(1, dormancyIntervalTicks);
    }

    public boolean isBulkEdits() {
        return bulkEdits;
    }

    public int getMaxBulkEditsPerTick() {
        return Math.max(1, maxBulkEditsPerTick);
    }

    public boolean isReuseFlushBuffers() {
        return reuseFlushBuffers;
    }

    public boolean isQuietLogging() {
        return quietLogging;
    }

    public boolean isMemoryGuard() {
        return memoryGuard;
    }

    public int getGuardViewDistance() {
        return Math.max(2, guardViewDistance);
    }

    public int getGuardMapRadius() {
        return Math.max(1, guardMapRadius);
    }

    public int getEntityHardCap() {
        return Math.max(0, entityHardCap);
    }

    public boolean isItemMerging() {
        return itemMerging;
    }

    public int getItemRadius() {
        return Math.max(2, itemMergingRadius);
    }

    public int getItemIntervalTicks() {
        return Math.max(1, itemMergingIntervalTicks);
    }

    public int getItemMaxLifetime() {
        return Math.max(0, itemMergingMaxLifetime);
    }

    public boolean isTreeFelling() {
        return treeFelling;
    }

    public int getTreeMaxBlocks() {
        return Math.max(8, treeMaxBlocks);
    }

    public boolean isGcStutterGuard() {
        return gcStutterGuard;
    }

    public boolean isFarView() {
        return farView;
    }

    public int getEntityViewDistance() {
        return Math.max(2, entityViewDistance);
    }

    public int getFarHotRadius() {
        return Math.max(2, farHotRadius);
    }

    public boolean isJoinBoost() {
        return joinBoost;
    }

    public int getJoinBoostPerSecond() {
        return Math.max(1, joinBoostPerSecond);
    }

    public int getJoinBoostPerTick() {
        return Math.max(1, joinBoostPerTick);
    }

    public int getMaxStreamPerTick() {
        return Math.max(0, maxStreamPerTick);
    }

    @Nonnull
    public String summarize() {
        return "asyncFetch=" + flag(asyncFetch)
            + " spawnBudget=" + flag(spawnBudget) + "(" + spawnBudgetPerTick + "/t)"
            + " chunkResolve=" + flag(chunkResolve) + "(" + maxResolvesPerTick + "/t)"
            + " heapSampling=" + flag(heapSampling)
            + " viewDistance=" + flag(viewDormancy) + "(view" + minViewDistance + "-" + maxViewDistance + ")"
            + " bulkEdits=" + flag(bulkEdits) + "(" + maxBulkEditsPerTick + "/t)"
            + " reuseFlushBuffers=" + flag(reuseFlushBuffers)
            + " quietLogging=" + flag(quietLogging)
            + " memoryGuard=" + flag(memoryGuard) + "(maxView" + guardViewDistance
            + "/mapView" + guardMapRadius + "/entityCap" + entityHardCap + ")"
            + " itemMerging=" + flag(itemMerging) + "(r" + itemMergingRadius + "/every" + itemMergingIntervalTicks + "/ttl" + itemMergingMaxLifetime + ")"
            + " treeFelling=" + flag(treeFelling) + "(max" + treeMaxBlocks + ")"
            + " gcStutterGuard=" + flag(gcStutterGuard)
            + " farView=" + flag(farView) + "(entity" + entityViewDistance + "/tick" + farHotRadius + ")"
            + " joinBoost=" + flag(joinBoost) + "(" + joinBoostPerSecond + "/s,"
            + joinBoostPerTick + "/t) streamCap" + maxStreamPerTick;
    }

    private static String flag(final boolean on) {
        return on ? "on" : "off";
    }
}
