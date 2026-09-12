package com.abo47.optimizationsandstuff.items;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PickupItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * Merges scattered dropped-item entities into stacked survivors. The engine
 * only merges within 2 blocks every 1.5s per entity; this sweeps globally
 * every ItemMergingIntervalTicks at ItemMergingRadius with no per-entity
 * delay. Same protocol as the engine (absorb via withQuantity, tombstone the
 * victim, TTL stays on the survivor). Entry objects are reused across scans.
 */
public class ItemMerging extends TickingSystem<EntityStore> {

    private static final int MAX_ENTRIES = 1024;

    private static final class Entry {
        Ref<EntityStore> ref;
        double x;
        double y;
        double z;
    }

    private final ArrayList<Entry> entries = new ArrayList<>(MAX_ENTRIES);
    private int entryCount;
    private long tickCounter;
    private long merged;
    private long removed;
    private long scans;
    private long ttlCapped;

    @Override
    public void tick(final float dt, final int systemIndex, @Nonnull final Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread()) {
            return;
        }
        TuningConfig config = TuningConfig.resolve(world);
        if (!config.isItemMerging()) {
            return;
        }
        tickCounter++;
        if (tickCounter % config.getItemIntervalTicks() != 0) {
            return;
        }
        scans++;
        gather(store);
        if (entryCount > 1) {
            mergeAll(store, config.getItemRadius());
        }
        capLifetimes(store, config.getItemMaxLifetime());
        for (int i = 0; i < entryCount; i++) {
            entries.get(i).ref = null;
        }
        entryCount = 0;
    }

    private void capLifetimes(@Nonnull final Store<EntityStore> store, final int maxLifetime) {
        if (maxLifetime <= 0 || entryCount == 0) {
            return;
        }
        TimeResource time;
        try {
            time = store.getResource(TimeResource.getResourceType());
            if (time == null || time.getNow() == null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        var despawnType = DespawnComponent.getComponentType();
        var cutoff = time.getNow().plusSeconds(maxLifetime);
        for (int i = 0; i < entryCount; i++) {
            Entry e = entries.get(i);
            if (e.ref == null || !e.ref.isValid()) {
                continue;
            }
            try {
                DespawnComponent despawn = store.getComponent(e.ref, despawnType);
                if (despawn == null) {
                    continue;
                }
                var at = despawn.getDespawn();
                if (at == null || at.isAfter(cutoff)) {
                    despawn.setDespawnTo(time.getNow(), maxLifetime);
                    ttlCapped++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void gather(@Nonnull final Store<EntityStore> store) {
        var itemType = ItemComponent.getComponentType();
        var transformType = TransformComponent.getComponentType();
        var noMergeType = PreventItemMerging.getComponentType();
        var pickupType = PickupItemComponent.getComponentType();
        var interactableType = Interactable.getComponentType();
        store.forEachChunk((archetypeChunk, commandBuffer) -> {
            int size = archetypeChunk.size();
            for (int i = 0; i < size && entryCount < MAX_ENTRIES; i++) {
                ItemComponent item = archetypeChunk.getComponent(i, itemType);
                if (item == null) {
                    continue;
                }
                if (archetypeChunk.getComponent(i, noMergeType) != null
                        || archetypeChunk.getComponent(i, pickupType) != null
                        || archetypeChunk.getComponent(i, interactableType) != null) {
                    continue;
                }
                var transform = archetypeChunk.getComponent(i, transformType);
                if (transform == null) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                if (stack == null || stack.getQuantity() <= 0) {
                    continue;
                }
                int maxStack;
                try {
                    maxStack = stack.getItem().getMaxStack();
                } catch (Exception e) {
                    continue;
                }
                if (maxStack <= 1 || stack.getQuantity() >= maxStack) {
                    continue;
                }
                var ref = archetypeChunk.getReferenceTo(i);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                Entry e;
                if (entryCount < entries.size()) {
                    e = entries.get(entryCount);
                } else {
                    e = new Entry();
                    entries.add(e);
                }
                var pos = transform.getPosition();
                e.ref = ref;
                e.x = pos.x;
                e.y = pos.y;
                e.z = pos.z;
                entryCount++;
            }
        });
    }

    private void mergeAll(@Nonnull final Store<EntityStore> store, final int radius) {
        var itemType = ItemComponent.getComponentType();
        double radiusSq = (double) radius * (double) radius;
        for (int i = 0; i < entryCount; i++) {
            Entry survivor = entries.get(i);
            if (survivor.ref == null || !survivor.ref.isValid()) {
                continue;
            }
            ItemComponent survivorComp = store.getComponent(survivor.ref, itemType);
            if (survivorComp == null) {
                survivor.ref = null;
                continue;
            }
            ItemStack stack = survivorComp.getItemStack();
            if (stack == null) {
                survivor.ref = null;
                continue;
            }
            int maxStack = stack.getItem().getMaxStack();
            if (maxStack <= 1 || stack.getQuantity() >= maxStack) {
                continue;
            }
            for (int j = i + 1; j < entryCount && stack.getQuantity() < maxStack; j++) {
                Entry victim = entries.get(j);
                if (victim.ref == null || !victim.ref.isValid()) {
                    continue;
                }
                double dx = victim.x - survivor.x;
                double dy = victim.y - survivor.y;
                double dz = victim.z - survivor.z;
                if (dx * dx + dy * dy + dz * dz > radiusSq) {
                    continue;
                }
                ItemComponent victimComp = store.getComponent(victim.ref, itemType);
                if (victimComp == null) {
                    victim.ref = null;
                    continue;
                }
                ItemStack other = victimComp.getItemStack();
                if (other == null || !stack.isStackableWith(other) || other.getQuantity() >= maxStack) {
                    continue;
                }
                int combined = stack.getQuantity() + other.getQuantity();
                if (combined <= maxStack) {
                    victimComp.setItemStack(null);
                    try {
                        store.removeEntity(victim.ref, RemoveReason.REMOVE);
                    } catch (Exception ignored) {
                    }
                    victim.ref = null;
                    removed++;
                    stack = stack.withQuantity(combined);
                    survivorComp.setItemStack(stack);
                    merged++;
                } else {
                    victimComp.setItemStack(stack.withQuantity(combined - maxStack));
                    stack = stack.withQuantity(maxStack);
                    survivorComp.setItemStack(stack);
                    merged++;
                }
            }
        }
    }

    public long getMerged() {
        return merged;
    }

    public long getRemoved() {
        return removed;
    }

    public long getScans() {
        return scans;
    }

    public long getTtlCapped() {
        return ttlCapped;
    }

    public void resetCounters() {
        merged = 0;
        removed = 0;
        scans = 0;
        ttlCapped = 0;
    }
}
