package com.abo47.optimizationsandstuff.config;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Raw settings file model. Every field is nullable so an older or hand-edited
 * file falls through to code defaults per field instead of failing the load.
 */
public class ModSettings {

    @Nullable
    @SerializedName("AsyncFetch")
    public Boolean asyncFetch;

    @Nullable
    @SerializedName("SpawnBudget")
    public Boolean spawnBudget;

    @Nullable
    @SerializedName("SpawnBudgetPerTick")
    public Integer spawnBudgetPerTick;

    @Nullable
    @SerializedName("ChunkResolve")
    public Boolean chunkResolve;

    @Nullable
    @SerializedName("MaxResolvesPerTick")
    public Integer maxResolvesPerTick;

    @Nullable
    @SerializedName("HeapSampling")
    public Boolean heapSampling;

    @Nullable
    @SerializedName("ViewDormancy")
    public Boolean viewDormancy;

    @Nullable
    @SerializedName("MinViewDistance")
    public Integer minViewDistance;

    @Nullable
    @SerializedName("MaxViewDistance")
    public Integer maxViewDistance;

    @Nullable
    @SerializedName("DormantMarginChunks")
    public Integer dormantMarginChunks;

    @Nullable
    @SerializedName("DormancyIntervalTicks")
    public Integer dormancyIntervalTicks;

    @Nullable
    @SerializedName("BulkEdits")
    public Boolean bulkEdits;

    @Nullable
    @SerializedName("MaxBulkEditsPerTick")
    public Integer maxBulkEditsPerTick;

    @Nullable
    @SerializedName("ReuseFlushBuffers")
    public Boolean reuseFlushBuffers;

    @Nullable
    @SerializedName("QuietLogging")
    public Boolean quietLogging;

    @Nullable
    @SerializedName("MemoryGuard")
    public Boolean memoryGuard;

    @Nullable
    @SerializedName("GuardViewDistance")
    public Integer guardViewDistance;

    @Nullable
    @SerializedName("GuardMapRadius")
    public Integer guardMapRadius;

    @Nullable
    @SerializedName("EntityHardCap")
    public Integer entityHardCap;

    @Nullable
    @SerializedName("ItemMerging")
    public Boolean itemMerging;

    @Nullable
    @SerializedName("ItemMergingRadius")
    public Integer itemMergingRadius;

    @Nullable
    @SerializedName("ItemMergingIntervalTicks")
    public Integer itemMergingIntervalTicks;

    @Nullable
    @SerializedName("TreeFelling")
    public Boolean treeFelling;

    @Nullable
    @SerializedName("TreeMaxBlocks")
    public Integer treeMaxBlocks;

    @Nullable
    @SerializedName("GcStutterGuard")
    public Boolean gcStutterGuard;

    @Nullable
    @SerializedName("FarView")
    public Boolean farView;

    @Nullable
    @SerializedName("EntityViewDistance")
    public Integer entityViewDistance;

    @Nullable
    @SerializedName("FarHotRadius")
    public Integer farHotRadius;

    @Nullable
    @SerializedName("JoinBoost")
    public Boolean joinBoost;

    @Nullable
    @SerializedName("JoinBoostPerSecond")
    public Integer joinBoostPerSecond;

    @Nullable
    @SerializedName("JoinBoostPerTick")
    public Integer joinBoostPerTick;

    @Nullable
    @SerializedName("MaxStreamPerTick")
    public Integer maxStreamPerTick;

    @Nonnull
    public static ModSettings withDefaults() {
        ModSettings settings = new ModSettings();
        settings.asyncFetch = true;
        settings.spawnBudget = true;
        settings.spawnBudgetPerTick = 8;
        settings.chunkResolve = true;
        settings.maxResolvesPerTick = 64;
        settings.heapSampling = true;
        settings.viewDormancy = true;
        settings.minViewDistance = 8;
        settings.maxViewDistance = 8;
        settings.dormantMarginChunks = 2;
        settings.dormancyIntervalTicks = 40;
        settings.bulkEdits = true;
        settings.maxBulkEditsPerTick = 256;
        settings.reuseFlushBuffers = true;
        settings.quietLogging = true;
        settings.memoryGuard = true;
        settings.guardViewDistance = 4;
        settings.guardMapRadius = 2;
        settings.entityHardCap = 1500;
        settings.itemMerging = true;
        settings.itemMergingRadius = 8;
        settings.itemMergingIntervalTicks = 20;
        settings.treeFelling = true;
        settings.treeMaxBlocks = 192;
        settings.gcStutterGuard = true;
        settings.farView = true;
        settings.entityViewDistance = 4;
        settings.farHotRadius = 6;
        settings.joinBoost = true;
        settings.joinBoostPerSecond = 1280;
        settings.joinBoostPerTick = 80;
        settings.maxStreamPerTick = 24;
        return settings;
    }
}
