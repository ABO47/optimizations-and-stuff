package com.abo47.optimizationsandstuff;

import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import com.abo47.optimizationsandstuff.benchmark.PresetTestRunner;
import com.abo47.optimizationsandstuff.chunks.ChunkResolveQueue;
import com.abo47.optimizationsandstuff.chunks.ChunkStreamingBudgets;
import com.abo47.optimizationsandstuff.commands.OptCommand;
import com.abo47.optimizationsandstuff.config.SettingsLoader;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.edits.BulkEditBuffer;
import com.abo47.optimizationsandstuff.items.ItemMerging;
import com.abo47.optimizationsandstuff.logging.ModLog;
import com.abo47.optimizationsandstuff.memory.EngineConstantPatches;
import com.abo47.optimizationsandstuff.memory.GcStutterGuard;
import com.abo47.optimizationsandstuff.memory.HeapDeltaSampler;
import com.abo47.optimizationsandstuff.memory.MemoryPressureGuard;
import com.abo47.optimizationsandstuff.spawning.SpawnBudget;
import com.abo47.optimizationsandstuff.trees.TreeFelling;
import com.abo47.optimizationsandstuff.view.FarViewOptimizer;
import com.abo47.optimizationsandstuff.view.ViewDormancy;

public class OptimizationsPlugin extends JavaPlugin {

    private static OptimizationsPlugin instance = null;

    private SpawnBudget spawnBudget;
    private ChunkResolveQueue chunkResolveQueue;
    private HeapDeltaSampler heapSampler;
    private ViewDormancy viewDormancy;
    private BulkEditBuffer bulkEditBuffer;
    private PresetTestRunner presetTestRunner;
    private MemoryPressureGuard memoryGuard;
    private ItemMerging itemMerging;
    private TreeFelling treeFelling;
    private GcStutterGuard gcStutterGuard;
    private FarViewOptimizer farViewOptimizer;
    private ChunkStreamingBudgets chunkStreamingBudgets;

    public OptimizationsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        SettingsLoader.init();
        ModLog.refresh();
        GameplayConfig.PLUGIN_CODEC.register(TuningConfig.class, TuningConfig.ID, TuningConfig.CODEC);

        spawnBudget = new SpawnBudget();
        chunkResolveQueue = new ChunkResolveQueue();
        heapSampler = new HeapDeltaSampler();
        viewDormancy = new ViewDormancy();
        bulkEditBuffer = new BulkEditBuffer();
        presetTestRunner = new PresetTestRunner();
        memoryGuard = new MemoryPressureGuard();
        itemMerging = new ItemMerging();
        treeFelling = new TreeFelling();
        gcStutterGuard = new GcStutterGuard();
        farViewOptimizer = new FarViewOptimizer();
        chunkStreamingBudgets = new ChunkStreamingBudgets();

        getEntityStoreRegistry().registerSystem(spawnBudget);
        getEntityStoreRegistry().registerSystem(chunkResolveQueue);
        getEntityStoreRegistry().registerSystem(heapSampler);
        getEntityStoreRegistry().registerSystem(viewDormancy);
        getEntityStoreRegistry().registerSystem(bulkEditBuffer);
        getEntityStoreRegistry().registerSystem(presetTestRunner);
        getEntityStoreRegistry().registerSystem(memoryGuard);
        getEntityStoreRegistry().registerSystem(itemMerging);
        getEntityStoreRegistry().registerSystem(treeFelling);
        getEntityStoreRegistry().registerSystem(gcStutterGuard);
        getEntityStoreRegistry().registerSystem(farViewOptimizer);
        getEntityStoreRegistry().registerSystem(chunkStreamingBudgets);

        EngineConstantPatches.apply();

        getCommandRegistry().registerCommand(new OptCommand());

        ModLog.info("Optimizations And Stuff loaded");
    }

    public static OptimizationsPlugin get() {
        return instance;
    }

    @Nonnull
    public String countersSnapshot() {
        return "spawnsOk=" + spawnBudget.getAcquired()
            + " spawnsShed=" + spawnBudget.getShed()
            + " resolveQueue=" + chunkResolveQueue.getPending()
            + " resolveDrained=" + chunkResolveQueue.getDrained()
            + " resolveDropped=" + chunkResolveQueue.getDropped()
            + " heapDeltaAvg=" + heapSampler.getAverageDelta()
            + " heapDeltaMax=" + heapSampler.getMaxDelta()
            + " heapSpikes=" + heapSampler.getSpikes()
            + " viewChecked=" + viewDormancy.getChecked()
            + " viewClamped=" + viewDormancy.getClamped()
            + " viewScans=" + viewDormancy.getScans()
            + " viewDormant=" + viewDormancy.getDormant()
            + " viewWoken=" + viewDormancy.getWoken()
            + " viewSections=" + viewDormancy.getLoadedSections()
            + " viewEntities=" + viewDormancy.getLoadedEntities()
            + " bulkApplied=" + bulkEditBuffer.getApplied()
            + " bulkUnchanged=" + bulkEditBuffer.getUnchanged()
            + " bulkDeferred=" + bulkEditBuffer.getDeferred()
            + " bulkDropped=" + bulkEditBuffer.getDropped()
            + " bulkSections=" + bulkEditBuffer.getSectionsTouched()
            + " bulkBatches=" + bulkEditBuffer.getBatchesFlushed()
            + " poolHits=" + bulkEditBuffer.getPoolHits()
            + " poolMisses=" + bulkEditBuffer.getPoolMisses()
            + " guardClamped=" + memoryGuard.getClamped()
            + " guardMap=" + memoryGuard.getMapOverrides()
            + " guardCulled=" + memoryGuard.getCulled()
            + " guardGc=" + memoryGuard.getGcHints()
            + " merged=" + itemMerging.getMerged()
            + " mergeRemoved=" + itemMerging.getRemoved()
            + " mergeScans=" + itemMerging.getScans()
            + " treesFelled=" + treeFelling.getTreesFelled()
            + " blocksFelled=" + treeFelling.getBlocksFelled()
            + " stutterMaxMs=" + gcStutterGuard.getStutterMaxMs()
            + " gcHints=" + gcStutterGuard.getHints()
            + " farShrunk=" + farViewOptimizer.getShrunk()
            + " farHot=" + farViewOptimizer.getHotCaps()
            + " boosted=" + chunkStreamingBudgets.getBoosted()
            + " streamCap=" + chunkStreamingBudgets.getCapped()
            + " logEmitted=" + ModLog.getEmitted()
            + " logSuppressed=" + ModLog.getSuppressed();
    }

    public SpawnBudget getSpawnBudget() {
        return spawnBudget;
    }

    public ChunkResolveQueue getChunkResolveQueue() {
        return chunkResolveQueue;
    }

    public HeapDeltaSampler getHeapSampler() {
        return heapSampler;
    }

    public ViewDormancy getViewDormancy() {
        return viewDormancy;
    }

    public BulkEditBuffer getBulkEditBuffer() {
        return bulkEditBuffer;
    }

    public PresetTestRunner getPresetTestRunner() {
        return presetTestRunner;
    }

    public MemoryPressureGuard getMemoryPressureGuard() {
        return memoryGuard;
    }

    public ItemMerging getItemMerging() {
        return itemMerging;
    }

    public TreeFelling getTreeFelling() {
        return treeFelling;
    }

    public GcStutterGuard getGcStutterGuard() {
        return gcStutterGuard;
    }

    public FarViewOptimizer getFarViewOptimizer() {
        return farViewOptimizer;
    }

    public ChunkStreamingBudgets getChunkStreamingBudgets() {
        return chunkStreamingBudgets;
    }

    public void resetCounters() {
        spawnBudget.resetCounters();
        chunkResolveQueue.resetCounters();
        heapSampler.resetCounters();
        viewDormancy.resetCounters();
        bulkEditBuffer.resetCounters();
        memoryGuard.resetCounters();
        itemMerging.resetCounters();
        treeFelling.resetCounters();
        gcStutterGuard.resetCounters();
        farViewOptimizer.resetCounters();
        chunkStreamingBudgets.resetCounters();
        ModLog.resetCounters();
    }
}
