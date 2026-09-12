package com.abo47.optimizationsandstuff.spawning;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;
/**
 * Caps mod-driven entity spawns per tick. Call sites borrow permits via
 * tryAcquire; excess spawns are shed and counted. MemoryGuard forces the
 * budget to 2 regardless of the configured value.
 */
public class SpawnBudget extends TickingSystem<EntityStore> {

    private int budget = Integer.MAX_VALUE;
    private long acquired;
    private long shed;

    public boolean tryAcquire() {
        if (budget <= 0) {
            shed++;
            return false;
        }
        budget--;
        acquired++;
        return true;
    }

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        TuningConfig config = TuningConfig.resolve(store.getExternalData().getWorld());
        if (config.isMemoryGuard()) {
            budget = 2;
            return;
        }
        budget = config.isSpawnBudget() ? config.getSpawnBudgetPerTick() : Integer.MAX_VALUE;
    }

    public long getAcquired() {
        return acquired;
    }

    public long getShed() {
        return shed;
    }

    public void resetCounters() {
        acquired = 0;
        shed = 0;
    }
}
