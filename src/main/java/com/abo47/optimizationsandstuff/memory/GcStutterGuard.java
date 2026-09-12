package com.abo47.optimizationsandstuff.memory;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;

/**
 * Worst-tick tracking in a bounded primitive ring plus GC hints only above
 * 80% heap (at most every 30s). Zero tick allocation: nanoTime diffs into a
 * long[] plus Runtime reads. JVM flags themselves must be set on the server
 * command line; the recipe is logged once.
 */
public class GcStutterGuard extends TickingSystem<EntityStore> {

    private static final int WINDOW = 600;
    private static final long MIN_HINT_INTERVAL_NANOS = 30_000_000_000L;

    private final Runtime runtime = Runtime.getRuntime();
    private final long[] ring = new long[WINDOW];
    private int pos;
    private int count;
    private long lastTickNanos = -1;
    private long maxTickNanos;
    private long lastHintNanos;
    private long hints;
    private boolean recipeLogged;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        TuningConfig config = TuningConfig.resolve(store.getExternalData().getWorld());
        if (!config.isGcStutterGuard()) {
            return;
        }
        long now = System.nanoTime();
        if (lastTickNanos >= 0) {
            long tick = now - lastTickNanos;
            ring[pos] = tick;
            pos = (pos + 1) % WINDOW;
            count++;
            if (tick > maxTickNanos) {
                maxTickNanos = tick;
            }
        }
        lastTickNanos = now;

        if (!recipeLogged) {
            recipeLogged = true;
            ModLog.warning("GcStutterGuard JVM recipe (server command line): "
                + "-XX:+UseZGC -XX:ZCollectionInterval=5 -Xmx4G "
                + "-XX:MaxDirectMemorySize=1G -Dio.netty.allocator.type=pooled; "
                + "ZGC makes System.gc() cheap, G1 needs -XX:MaxGCPauseMillis=50 instead.");
        }

        if (now - lastHintNanos < MIN_HINT_INTERVAL_NANOS) {
            return;
        }
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (max > 0 && used > max * 8L / 10L) {
            lastHintNanos = now;
            hints++;
            System.gc();
        }
    }

    public double getStutterMaxMs() {
        return maxTickNanos / 1_000_000.0;
    }

    public double getTickAvgMs() {
        int samples = Math.min(count, WINDOW);
        if (samples == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += ring[i];
        }
        return sum / 1_000_000.0 / samples;
    }

    public long getHints() {
        return hints;
    }

    public void resetCounters() {
        pos = 0;
        count = 0;
        lastTickNanos = -1;
        maxTickNanos = 0;
        hints = 0;
    }
}
