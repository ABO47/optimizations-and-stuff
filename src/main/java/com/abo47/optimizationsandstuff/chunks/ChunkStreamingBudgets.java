package com.abo47.optimizationsandstuff.chunks;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;

/**
 * Chunk streaming paces. JoinBoost raises per-player section budgets so
 * joins/teleports fill faster (short bandwidth burst); MaxStreamPerTick caps
 * per-tick issue to spread bytes over more ticks when moving fast
 * (0 = engine default). Both apply idempotently; disabling returns control
 * to engine defaults applied on join/transfer/slider.
 */
public class ChunkStreamingBudgets extends TickingSystem<EntityStore> {

    private long boosted;
    private long capped;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread()) {
            return;
        }
        TuningConfig config = TuningConfig.resolve(world);
        boolean boost = config.isJoinBoost();
        int cap = config.getMaxStreamPerTick();
        if (!boost && cap <= 0) {
            return;
        }
        int perSecond = config.getJoinBoostPerSecond();
        int perTick = boost ? config.getJoinBoostPerTick() : cap;
        if (cap > 0 && perTick > cap) {
            perTick = cap;
        }
        var trackerType = ChunkTracker.getComponentType();
        var entityStore = world.getEntityStore().getStore();
        for (var playerRef : world.getPlayerRefs()) {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            try {
                var tracker = entityStore.getComponent(ref, trackerType);
                if (tracker == null) {
                    continue;
                }
                if (boost) {
                    tracker.setMaxSectionsPerSecond(perSecond);
                    tracker.setMaxSectionsPerTick(perTick);
                    boosted++;
                } else {
                    tracker.setMaxSectionsPerTick(perTick);
                    capped++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public long getBoosted() {
        return boosted;
    }

    public long getCapped() {
        return capped;
    }

    public void resetCounters() {
        boosted = 0;
        capped = 0;
    }
}
