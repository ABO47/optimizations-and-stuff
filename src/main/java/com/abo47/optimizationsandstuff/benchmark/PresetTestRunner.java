package com.abo47.optimizationsandstuff.benchmark;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.abo47.optimizationsandstuff.OptimizationsPlugin;
import com.abo47.optimizationsandstuff.config.ModSettings;
import com.abo47.optimizationsandstuff.config.SettingsLoader;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unattended single-preset benchmark tests. Applies a preset, opens a bench
 * session, shows a live toast HUD, then auto-stops with a result row and
 * restores prior settings. All on the world tick thread; wall-clock timing
 * holds the duration even at low TPS.
 */
public class PresetTestRunner extends TickingSystem<EntityStore> {

    private static final long HUD_INTERVAL_NANOS = 5_000_000_000L;

    private static final class ActiveTest {
        final String preset;
        final String flagsLabel;
        final long startNanos;
        final long endNanos;
        final ModSettings prior;
        long lastHudNanos;

        ActiveTest(final String preset, final String flagsLabel, final long startNanos,
                final long endNanos, final ModSettings prior) {
            this.preset = preset;
            this.flagsLabel = flagsLabel;
            this.startNanos = startNanos;
            this.endNanos = endNanos;
            this.prior = prior;
            this.lastHudNanos = 0;
        }
    }

    private final Map<String, ActiveTest> active = new ConcurrentHashMap<>();

    @Nonnull
    private static String counters() {
        var plugin = OptimizationsPlugin.get();
        return plugin != null ? plugin.countersSnapshot() : "";
    }

    public boolean hasTest(@Nonnull final World world) {
        return active.containsKey(world.getName());
    }

    @Nullable
    public String begin(@Nonnull final World world, @Nonnull final FlagPresets.Preset preset, final int minutes) {
        if (active.containsKey(world.getName())) {
            return "a test is already running in '" + world.getName() + "'. Use /opt test stop first.";
        }
        ModSettings prior = SettingsLoader.snapshot();
        ModSettings next = SettingsLoader.snapshot();
        FlagPresets.apply(next, preset);
        SettingsLoader.save(next);

        var plugin = OptimizationsPlugin.get();
        if (plugin != null) {
            plugin.resetCounters();
        }
        String flags = TuningConfig.resolve(world).summarize();
        BenchmarkRecorder.start(world, flags);
        long now = System.nanoTime();
        active.put(world.getName(),
            new ActiveTest(preset.name, flags, now, now + minutes * 60_000_000_000L, prior));
        return null;
    }

    @Nullable
    public String cancel(@Nonnull final World world) {
        ActiveTest test = active.remove(world.getName());
        if (test == null) {
            return null;
        }
        SettingsLoader.save(test.prior);
        return BenchmarkRecorder.formatRow(
            BenchmarkRecorder.stop(world, "cancelled " + test.preset, test.flagsLabel, counters()));
    }

    @Nonnull
    public String status(@Nonnull final World world) {
        ActiveTest test = active.get(world.getName());
        if (test == null) {
            return "opttest: no test running. Presets: " + String.join(" ", FlagPresets.names());
        }
        long left = Math.max(0, test.endNanos - System.nanoTime());
        return "opttest '" + test.preset + "' [" + test.flagsLabel + "] "
            + (left / 60_000_000_000L) + "m" + ((left / 1_000_000_000L) % 60) + "s left.";
    }

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        if (active.isEmpty()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        ActiveTest test = active.get(world.getName());
        if (test == null) {
            return;
        }
        long now = System.nanoTime();
        if (now >= test.endNanos) {
            active.remove(world.getName());
            SettingsLoader.save(test.prior);
            String row = BenchmarkRecorder.formatRow(
                BenchmarkRecorder.stop(world, "finished " + test.preset, test.flagsLabel, counters()));
            world.sendMessage(Message.raw(row));
            return;
        }
        if (now - test.lastHudNanos >= HUD_INTERVAL_NANOS) {
            test.lastHudNanos = now;
            long left = test.endNanos - now;
            NotificationUtil.sendNotificationToWorld(
                Message.raw("opttest '" + test.preset + "': " + (left / 60_000_000_000L) + "m"
                    + ((left / 1_000_000_000L) % 60) + "s left"),
                null, null, null, NotificationStyle.Default, store);
        }
    }
}
