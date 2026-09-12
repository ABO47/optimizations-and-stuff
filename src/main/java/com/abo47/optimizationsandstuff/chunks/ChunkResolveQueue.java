package com.abo47.optimizationsandstuff.chunks;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import javax.annotation.Nonnull;

/**
 * Paces queued mod chunk resolves under a per-tick budget so bulk work cannot
 * burst the chunk tracker. Tick-thread only.
 */
public class ChunkResolveQueue extends TickingSystem<EntityStore> {

    private static final int MAX_QUEUE = 4096;

    private final LongArrayList queue = new LongArrayList();
    private int head;
    private long drained;
    private long dropped;

    public void enqueue(final long chunkIndex) {
        if (queue.size() >= MAX_QUEUE) {
            dropped++;
            return;
        }
        queue.add(chunkIndex);
    }

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        if (head >= queue.size()) {
            queue.clear();
            head = 0;
            return;
        }
        World world = store.getExternalData().getWorld();
        TuningConfig config = TuningConfig.resolve(world);
        long droppedBefore = dropped;
        int limit = config.isChunkResolve() ? config.getMaxResolvesPerTick() : Integer.MAX_VALUE;
        boolean async = ChunkAccess.useAsyncPaths(world);
        int remaining = queue.size() - head;
        int count = Math.min(limit, remaining);
        for (int i = 0; i < count; i++) {
            long chunkIndex = queue.getLong(head++);
            if (async) {
                ChunkAccess.getChunkAsync(world, chunkIndex);
            } else {
                world.getChunkStore().getChunkReference(chunkIndex);
            }
            drained++;
        }
        if (head >= queue.size()) {
            queue.clear();
            head = 0;
        } else if (head > 1024) {
            queue.removeElements(0, head);
            head = 0;
        }
        if (dropped > droppedBefore) {
            ModLog.info("resolve queue=" + getPending() + " drained=" + drained + " dropped=" + dropped);
        }
    }

    public int getPending() {
        return queue.size() - head;
    }

    public long getDrained() {
        return drained;
    }

    public long getDropped() {
        return dropped;
    }

    public void resetCounters() {
        drained = 0;
        dropped = 0;
    }
}
