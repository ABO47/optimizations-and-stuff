package com.abo47.optimizationsandstuff.view;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;

/**
 * Big chunk radius without the entity bill. The engine ties chunk loading
 * (ChunkTracker:356) and entity visibility (CollectVisible:489) to one player
 * radius; this forces only the entity side (EntityViewer.viewRadiusBlocks,
 * written by the engine solely on join/transfer/slider/commands) down to
 * EntityViewDistance while chunks stay big, and caps the block-ticking hot
 * radius so the far shell stays loaded but cold.
 */
public class FarViewOptimizer extends TickingSystem<EntityStore> {

    private long shrunk;
    private long hotCaps;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread()) {
            return;
        }
        TuningConfig config = TuningConfig.resolve(world);
        if (!config.isFarView()) {
            return;
        }
        int entityBlocks = config.getEntityViewDistance() * ChunkUtil.SIZE;
        int hotRadius = config.getFarHotRadius();
        var viewerType = EntityTrackerSystems.EntityViewer.getComponentType();
        var trackerType = ChunkTracker.getComponentType();
        var entityStore = world.getEntityStore().getStore();
        for (var playerRef : world.getPlayerRefs()) {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            var viewer = entityStore.getComponent(ref, viewerType);
            if (viewer != null && viewer.viewRadiusBlocks != entityBlocks) {
                viewer.viewRadiusBlocks = entityBlocks;
                shrunk++;
            }
            try {
                var tracker = entityStore.getComponent(ref, trackerType);
                if (tracker != null && tracker.getMaxHotLoadedRadius() != hotRadius) {
                    tracker.setMaxHotLoadedRadius(hotRadius);
                    hotCaps++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public long getShrunk() {
        return shrunk;
    }

    public long getHotCaps() {
        return hotCaps;
    }

    public void resetCounters() {
        shrunk = 0;
        hotCaps = 0;
    }
}
