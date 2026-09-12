package com.abo47.optimizationsandstuff.memory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Hard view/map caps, a global non-player entity cap (farthest despawned),
 * and heap-threshold GC hints. Fork-only work (final engine constants) lives
 * in EngineConstantPatches.
 */
public class MemoryPressureGuard extends TickingSystem<EntityStore> {

    private static final int MAX_PLAYERS = 64;
    private static final int CULL_SCAN_INTERVAL_TICKS = 100;
    private static final int GC_HINT_INTERVAL_TICKS = 1200;

    private final double[] playerX = new double[MAX_PLAYERS];
    private final double[] playerY = new double[MAX_PLAYERS];
    private final double[] playerZ = new double[MAX_PLAYERS];
    private final ArrayList<Candidate> victims = new ArrayList<>(512);
    private int victimCount;

    private long tickCounter;
    private long clamped;
    private long mapOverrides;
    private long culled;
    private long gcHints;

    private static final class Candidate {
        Ref<EntityStore> ref;
        double distSq;
    }

    private static final Comparator<Candidate> FARTHEST_FIRST =
        (a, b) -> Double.compare(b.distSq, a.distSq);

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread()) {
            return;
        }
        TuningConfig config = TuningConfig.resolve(world);
        if (!config.isMemoryGuard()) {
            return;
        }
        tickCounter++;

        clampViews(world, config);
        if (tickCounter % CULL_SCAN_INTERVAL_TICKS == 0) {
            cullEntities(world, store, config);
        }
        if (tickCounter % GC_HINT_INTERVAL_TICKS == 0) {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory();
            long used = rt.totalMemory() - rt.freeMemory();
            if (max <= 0 || used > max * 8L / 10L) {
                gcHints++;
                System.gc();
                ModLog.warning("memoryGuard heap=" + (used / 1048576L)
                    + "MB culled=" + culled + " clamped=" + clamped);
            }
        }
    }

    private void clampViews(@Nonnull final World world, @Nonnull final TuningConfig config) {
        int cap = Math.min(config.getMaxViewDistance(), config.getGuardViewDistance());
        int mapRadius = config.getGuardMapRadius();
        var playerType = Player.getComponentType();
        var entityStore = world.getEntityStore().getStore();
        for (var playerRef : world.getPlayerRefs()) {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Player player = entityStore.getComponent(ref, playerType);
            if (player == null) {
                continue;
            }
            if (player.getViewRadius() > cap) {
                player.setClientViewRadius(cap);
                clamped++;
            }
            try {
                Integer override = player.getWorldMapTracker().getViewRadiusOverride();
                if (override == null || override != mapRadius) {
                    player.getWorldMapTracker().setViewRadiusOverride(mapRadius);
                    mapOverrides++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void cullEntities(@Nonnull final World world, @Nonnull final Store<EntityStore> store,
            @Nonnull final TuningConfig config) {
        int cap = config.getEntityHardCap();
        if (cap <= 0) {
            return;
        }
        int players = collectPlayers(world, store);
        var entityStore = world.getEntityStore().getStore();
        var playerType = Player.getComponentType();
        var transformType = TransformComponent.getComponentType();

        final int[] nonPlayers = new int[1];
        entityStore.forEachChunk((archetypeChunk, commandBuffer) -> {
            int size = archetypeChunk.size();
            for (int i = 0; i < size; i++) {
                if (archetypeChunk.getComponent(i, playerType) == null) {
                    nonPlayers[0]++;
                }
            }
        });
        if (nonPlayers[0] <= cap) {
            return;
        }

        victimCount = 0;
        entityStore.forEachChunk((archetypeChunk, commandBuffer) -> {
            int size = archetypeChunk.size();
            for (int i = 0; i < size; i++) {
                if (victimCount >= 4096) {
                    return;
                }
                if (archetypeChunk.getComponent(i, playerType) != null) {
                    continue;
                }
                var transform = archetypeChunk.getComponent(i, transformType);
                double distSq = transform != null ? minDistSq(transform, players) : Double.MAX_VALUE;
                var ref = archetypeChunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                Candidate c;
                if (victimCount < victims.size()) {
                    c = victims.get(victimCount);
                } else {
                    c = new Candidate();
                    victims.add(c);
                }
                victimCount++;
                c.ref = ref;
                c.distSq = distSq;
            }
        });
        victims.subList(victimCount, victims.size()).clear();
        victims.sort(FARTHEST_FIRST);
        int excess = nonPlayers[0] - cap;
        int removed = 0;
        for (int i = 0; i < excess && i < victims.size(); i++) {
            Candidate c = victims.get(i);
            if (c.ref != null && c.ref.isValid()) {
                try {
                    entityStore.removeEntity(c.ref, RemoveReason.REMOVE);
                    removed++;
                } catch (Exception ignored) {
                }
                c.ref = null;
            }
        }
        culled += removed;
        for (Candidate c : victims) {
            c.ref = null;
        }
        ModLog.warning("memoryGuard entity cap=" + cap + " had=" + nonPlayers[0] + " culled=" + removed);
    }

    private int collectPlayers(@Nonnull final World world, @Nonnull final Store<EntityStore> store) {
        var transformType = TransformComponent.getComponentType();
        int count = 0;
        for (var playerRef : world.getPlayerRefs()) {
            if (count >= MAX_PLAYERS) {
                break;
            }
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            var transform = store.getComponent(ref, transformType);
            if (transform == null) {
                continue;
            }
            var pos = transform.getPosition();
            playerX[count] = pos.x;
            playerY[count] = pos.y;
            playerZ[count] = pos.z;
            count++;
        }
        return count;
    }

    private double minDistSq(@Nonnull final TransformComponent transform, final int players) {
        if (players == 0) {
            return 0.0;
        }
        var pos = transform.getPosition();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < players; i++) {
            double dx = pos.x - playerX[i];
            double dy = pos.y - playerY[i];
            double dz = pos.z - playerZ[i];
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    public long getClamped() {
        return clamped;
    }

    public long getMapOverrides() {
        return mapOverrides;
    }

    public long getCulled() {
        return culled;
    }

    public long getGcHints() {
        return gcHints;
    }

    public void resetCounters() {
        clamped = 0;
        mapOverrides = 0;
        culled = 0;
        gcHints = 0;
    }
}
