package com.abo47.optimizationsandstuff.view;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import com.abo47.optimizationsandstuff.logging.ModLog;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * Bounds loaded state two ways: clamps each player's view radius into
 * [MinViewDistance, MaxViewDistance] every tick, and parks sections beyond
 * max + margin as NonTicking on a throttled scan. FarView also runs the scan
 * without clamping the chunk radius.
 */
public class ViewDormancy extends TickingSystem<EntityStore> {

    private static final Query<ChunkStore> SECTION_QUERY = Query.and(ChunkSection.getComponentType());
    private static final int MAX_TRACKED_PLAYERS = 64;
    private static final int MAX_MUTATIONS_PER_SCAN = 512;

    private final int[] playerChunkX = new int[MAX_TRACKED_PLAYERS];
    private final int[] playerChunkZ = new int[MAX_TRACKED_PLAYERS];
    private final ArrayList<Ref<ChunkStore>> toPark = new ArrayList<>(MAX_MUTATIONS_PER_SCAN);
    private final ArrayList<Ref<ChunkStore>> toWake = new ArrayList<>(MAX_MUTATIONS_PER_SCAN);

    private long tickCounter;
    private long checked;
    private long clamped;
    private long scans;
    private long dormant;
    private long woken;
    private long loadedSections;
    private long loadedEntities;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread()) {
            return;
        }
        TuningConfig config = TuningConfig.resolve(world);
        boolean dormancy = config.isViewDormancy();
        boolean far = config.isFarView();
        if (!dormancy && !far) {
            return;
        }
        tickCounter++;

        if (dormancy) {
            int min = config.getMinViewDistance();
            int max = config.getMaxViewDistance();
            var playerType = Player.getComponentType();
            for (var playerRef : world.getPlayerRefs()) {
                var ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                Player player = store.getComponent(ref, playerType);
                if (player == null) {
                    continue;
                }
                checked++;
                if (player.getViewRadius() > max) {
                    player.setClientViewRadius(max);
                    clamped++;
                } else if (player.getViewRadius() < min) {
                    player.setClientViewRadius(min);
                    clamped++;
                }
            }
        }

        if (tickCounter % config.getDormancyIntervalTicks() != 0) {
            return;
        }
        runDormancyScan(store, world, config);
    }

    private void runDormancyScan(@Nonnull final Store<EntityStore> store, @Nonnull final World world,
            @Nonnull final TuningConfig config) {
        int players = collectPlayerChunks(store, world);
        if (players == 0) {
            return;
        }
        int radius = config.getMaxViewDistance() + config.getDormantMarginChunks();
        var nonTickingType = ChunkStore.REGISTRY.getNonTickingComponentType();
        var chunkEcs = world.getChunkStore().getStore();

        // Read-only collect first. Mutating chunk components inside forEachChunk
        // consumes the command buffer mid-iteration, cascading into entity
        // add/remove while the chunk store is walked - that crashed the world
        // (NPE in SpawnSuppressionSystems$Suppressor.onEntityRemove).
        toPark.clear();
        toWake.clear();
        final long[] seen = new long[1];
        chunkEcs.forEachChunk(SECTION_QUERY, (archetypeChunk, commandBuffer) -> {
            int size = archetypeChunk.size();
            for (int i = 0; i < size; i++) {
                ChunkSection section = archetypeChunk.getComponent(i, ChunkSection.getComponentType());
                if (section == null) {
                    continue;
                }
                seen[0]++;
                boolean far = isFar(section.getX(), section.getZ(), players, radius);
                var ref = archetypeChunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                boolean dormantNow = archetypeChunk.getComponent(i, nonTickingType) != null;
                if (far && !dormantNow) {
                    if (toPark.size() < MAX_MUTATIONS_PER_SCAN) {
                        toPark.add(ref);
                    }
                } else if (!far && dormantNow) {
                    if (toWake.size() < MAX_MUTATIONS_PER_SCAN) {
                        toWake.add(ref);
                    }
                }
            }
        });

        long parked = 0;
        for (Ref<ChunkStore> ref : toPark) {
            try {
                if (ref != null && ref.isValid()) {
                    chunkEcs.ensureComponent(ref, nonTickingType);
                    parked++;
                }
            } catch (Exception e) {
                ModLog.warning("dormancy park failed: " + e.getMessage());
            }
        }
        long awakened = 0;
        for (Ref<ChunkStore> ref : toWake) {
            try {
                if (ref != null && ref.isValid()) {
                    chunkEcs.tryRemoveComponent(ref, nonTickingType);
                    awakened++;
                }
            } catch (Exception e) {
                ModLog.warning("dormancy wake failed: " + e.getMessage());
            }
        }
        toPark.clear();
        toWake.clear();

        scans++;
        dormant += parked;
        woken += awakened;
        loadedSections = seen[0];
        loadedEntities = sampleEntities(world);
        ModLog.info("dormancy scan players=" + players + " radius=" + radius
            + " sections=" + seen[0] + " parked=" + parked + " woken=" + awakened);
    }

    private int collectPlayerChunks(@Nonnull final Store<EntityStore> store, @Nonnull final World world) {
        int count = 0;
        var transformType = TransformComponent.getComponentType();
        for (var playerRef : world.getPlayerRefs()) {
            if (count >= MAX_TRACKED_PLAYERS) {
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
            playerChunkX[count] = (int) Math.floor(pos.x / 32.0);
            playerChunkZ[count] = (int) Math.floor(pos.z / 32.0);
            count++;
        }
        return count;
    }

    private boolean isFar(final int chunkX, final int chunkZ, final int players, final int radius) {
        for (int i = 0; i < players; i++) {
            int dx = Math.abs(chunkX - playerChunkX[i]);
            int dz = Math.abs(chunkZ - playerChunkZ[i]);
            if (Math.max(dx, dz) <= radius) {
                return false;
            }
        }
        return true;
    }

    private static long sampleEntities(@Nonnull final World world) {
        final long[] total = new long[1];
        try {
            world.getEntityStore().getStore().forEachChunk((archetypeChunk, commandBuffer) -> {
                total[0] += archetypeChunk.size();
            });
        } catch (Exception ignored) {
        }
        return total[0];
    }

    public long getChecked() {
        return checked;
    }

    public long getClamped() {
        return clamped;
    }

    public long getScans() {
        return scans;
    }

    public long getDormant() {
        return dormant;
    }

    public long getWoken() {
        return woken;
    }

    public long getLoadedSections() {
        return loadedSections;
    }

    public long getLoadedEntities() {
        return loadedEntities;
    }

    public void resetCounters() {
        checked = 0;
        clamped = 0;
        scans = 0;
        dormant = 0;
        woken = 0;
        loadedSections = 0;
        loadedEntities = 0;
    }
}
