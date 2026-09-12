package com.abo47.optimizationsandstuff.memory;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;

/**
 * Heap-delta ring while HeapSampling is on; spikes count deltas over 4MiB.
 * Sampling only - it measures allocation churn, it does not prevent it.
 */
public class HeapDeltaSampler extends TickingSystem<EntityStore> {

    private static final int WINDOW = 120;
    private static final long SPIKE_BYTES = 4L * 1024L * 1024L;

    private final Runtime runtime = Runtime.getRuntime();
    private final long[] ring = new long[WINDOW];
    private int pos;
    private int count;
    private long lastUsed = -1;
    private long maxDelta;
    private long spikes;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        TuningConfig config = TuningConfig.resolve(store.getExternalData().getWorld());
        if (!config.isHeapSampling()) {
            return;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (lastUsed >= 0) {
            long delta = used - lastUsed;
            ring[pos] = delta;
            pos = (pos + 1) % WINDOW;
            count++;
            long abs = Math.abs(delta);
            if (abs > maxDelta) {
                maxDelta = abs;
            }
            if (abs > SPIKE_BYTES) {
                spikes++;
            }
        }
        lastUsed = used;
    }

    public double getAverageDelta() {
        int samples = Math.min(count, WINDOW);
        if (samples == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += ring[i];
        }
        return sum / (double) samples;
    }

    public long getMaxDelta() {
        return maxDelta;
    }

    public long getSpikes() {
        return spikes;
    }

    public void resetCounters() {
        pos = 0;
        count = 0;
        lastUsed = -1;
        maxDelta = 0;
        spikes = 0;
    }
}
