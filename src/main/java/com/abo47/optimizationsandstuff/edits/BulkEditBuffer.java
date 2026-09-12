package com.abo47.optimizationsandstuff.edits;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Buffers mod-driven block edits and flushes them sorted by chunk section
 * under a per-tick budget. The engine drains each section once per tick into
 * one packet batch; sorting resolves each section's components once and lands
 * its edits back-to-back, and quiet settings skip per-block FX while keeping
 * state, heightmap and filler behavior.
 */
public class BulkEditBuffer extends TickingSystem<EntityStore> {

    /** Quiet batch settings: skip per-block FX/events, keep state/heightmap/filler. */
    private static final int BATCH_SETTINGS = SetBlockSettings.NO_SEND_PARTICLES
        | SetBlockSettings.NO_SEND_AUDIO
        | SetBlockSettings.NO_DROP_ITEMS
        | SetBlockSettings.NO_FIRE_ON_BREAK
        | SetBlockSettings.NO_UPDATE_NEIGHBOR_CONNECTIONS;

    private static final int MAX_PENDING = 16384;
    private static final int MAX_ATTEMPTS = 5;

    private static final Comparator<Edit> BY_SECTION =
        Comparator.comparingLong((Edit e) -> e.sectionKey);

    public static final class Edit {
        final int x;
        final int y;
        final int z;
        final int id;
        final long sectionKey;
        final BlockType type;
        int attempts;

        private Edit(final int x, final int y, final int z, final int id, final long sectionKey,
                @Nonnull final BlockType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.sectionKey = sectionKey;
            this.type = type;
        }
    }

    private final ObjectArrayList<Edit> pending = new ObjectArrayList<>();
    private final FlushBufferPool<ArrayList<Edit>> batchPool = new FlushBufferPool<>(4, () -> new ArrayList<>(512));
    private final FlushBufferPool<ArrayList<Edit>> spillPool = new FlushBufferPool<>(4, () -> new ArrayList<>(256));
    private int head;
    private long applied;
    private long unchanged;
    private long deferred;
    private long dropped;
    private long sectionsTouched;
    private long batchesFlushed;

    public boolean enqueue(final int x, final int y, final int z, @Nonnull final String blockTypeKey) {
        int id = BlockType.getAssetMap().getIndex(blockTypeKey);
        if (id == AssetMapWithIndexes.NOT_FOUND) {
            throw new IllegalArgumentException("Unknown block: " + blockTypeKey);
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockTypeKey);
        if (type == null) {
            throw new IllegalArgumentException("Unknown block: " + blockTypeKey);
        }
        if (pending.size() - head >= MAX_PENDING) {
            dropped++;
            return false;
        }
        pending.add(new Edit(x, y, z, id, sectionKeyOf(x, y, z), type));
        return true;
    }

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        if (head >= pending.size()) {
            if (head > 0) {
                pending.clear();
                head = 0;
            }
            return;
        }
        World world = store.getExternalData().getWorld();
        TuningConfig config = TuningConfig.resolve(world);
        int limit = config.isBulkEdits() ? config.getMaxBulkEditsPerTick() : Integer.MAX_VALUE;
        boolean pooled = config.isReuseFlushBuffers();
        ChunkStore chunkStore = world.getChunkStore();
        var chunkEcs = chunkStore.getStore();

        int remaining = pending.size() - head;
        int count = Math.min(limit, remaining);

        ArrayList<Edit> batch = pooled ? batchPool.borrow() : new ArrayList<>(count);
        batch.clear();
        for (int i = 0; i < count; i++) {
            batch.add(pending.get(head + i));
        }
        head += count;
        batch.sort(BY_SECTION);

        ArrayList<Edit> spill = pooled ? spillPool.borrow() : new ArrayList<>();
        spill.clear();

        long lastKey = Long.MIN_VALUE;
        Ref<ChunkStore> lastRef = null;
        ChunkSection section = null;
        BlockSection blockSection = null;
        BlockComponentSection blockComponentSection = null;
        BlockChunk blockChunk = null;

        for (int i = 0, size = batch.size(); i < size; i++) {
            Edit edit = batch.get(i);
            if (edit.sectionKey != lastKey) {
                lastKey = edit.sectionKey;
                lastRef = null;
                section = null;
                blockSection = null;
                blockComponentSection = null;
                blockChunk = null;
                Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(edit.x, edit.y, edit.z);
                if (sectionRef == null || !sectionRef.isValid()) {
                    park(spill, edit);
                    continue;
                }
                lastRef = sectionRef;
                section = chunkEcs.getComponent(sectionRef, ChunkSection.getComponentType());
                blockSection = chunkEcs.getComponent(sectionRef, BlockSection.getComponentType());
                blockComponentSection = chunkEcs.getComponent(sectionRef, BlockComponentSection.getComponentType());
                var columnRef = section != null ? section.getChunkColumnReference() : null;
                blockChunk = columnRef != null && columnRef.isValid()
                    ? chunkEcs.getComponent(columnRef, BlockChunk.getComponentType())
                    : null;
                sectionsTouched++;
            }
            if (lastRef == null || section == null || blockSection == null || blockChunk == null) {
                park(spill, edit);
                continue;
            }
            boolean changed = BlockOperations.setBlock(chunkStore, lastRef, section, blockSection,
                blockComponentSection, blockChunk, edit.x, edit.y, edit.z, edit.id, edit.type,
                RotationTuple.NONE_INDEX, FillerBlockUtil.NO_FILLER, BATCH_SETTINGS);
            if (changed) {
                applied++;
            } else {
                unchanged++;
            }
        }
        batchesFlushed++;

        long deferredBefore = deferred;
        long droppedBefore = dropped;
        for (int i = 0, size = spill.size(); i < size; i++) {
            pending.add(spill.get(i));
        }
        spill.clear();
        batch.clear();
        if (pooled) {
            batchPool.release(batch);
            spillPool.release(spill);
        }
        if (head >= pending.size()) {
            pending.clear();
            head = 0;
        } else if (head > 1024) {
            pending.removeElements(0, head);
            head = 0;
        }
        if (dropped > droppedBefore || deferred - deferredBefore > 0) {
            ModLog.info("edits flush applied=" + applied + " deferred=" + deferred
                + " dropped=" + dropped + " pending=" + getPending());
        }
    }

    public int getPending() {
        return pending.size() - head;
    }

    public long getApplied() {
        return applied;
    }

    public long getUnchanged() {
        return unchanged;
    }

    public long getDeferred() {
        return deferred;
    }

    public long getDropped() {
        return dropped;
    }

    public long getSectionsTouched() {
        return sectionsTouched;
    }

    public long getBatchesFlushed() {
        return batchesFlushed;
    }

    public long getPoolHits() {
        return batchPool.getHits() + spillPool.getHits();
    }

    public long getPoolMisses() {
        return batchPool.getMisses() + spillPool.getMisses();
    }

    public void resetCounters() {
        applied = 0;
        unchanged = 0;
        deferred = 0;
        dropped = 0;
        sectionsTouched = 0;
        batchesFlushed = 0;
        batchPool.resetCounters();
        spillPool.resetCounters();
    }

    private void park(@Nonnull final ArrayList<Edit> spill, @Nonnull final Edit edit) {
        edit.attempts++;
        if (edit.attempts > MAX_ATTEMPTS) {
            dropped++;
            return;
        }
        deferred++;
        spill.add(edit);
    }

    private static long sectionKeyOf(final int x, final int y, final int z) {
        long cx = ChunkUtil.chunkCoordinate(x);
        long cy = ChunkUtil.chunkCoordinate(y);
        long cz = ChunkUtil.chunkCoordinate(z);
        return (cx << 42) ^ ((cy & 0x1FFFFFL) << 21) ^ (cz & 0x1FFFFFL);
    }
}
