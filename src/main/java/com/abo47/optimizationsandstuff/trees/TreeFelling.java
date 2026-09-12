package com.abo47.optimizationsandstuff.trees;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Instant vanilla tree batch. A broken block backed by a same-id vertical run
 * of 3+ is a trunk: the break is cancelled and the whole connected column
 * plus nearby Plant_Leaves* is re-broken through the engine's own break path
 * in the same tick. Drops, physics, protection hooks stay vanilla; only the
 * multi-tick cascade collapses into one tick, FX stays on the first block,
 * and scattered drops collapse via itemMerging.
 */
public class TreeFelling extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final int QUIET_REST = SetBlockSettings.NO_SEND_PARTICLES
        | SetBlockSettings.NO_SEND_AUDIO
        | SetBlockSettings.NO_FIRE_ON_BREAK;

    private static final int MIN_TRUNK = 3;
    private static final int TRUNK_SCAN_UP = 40;
    private static final int LEAF_RADIUS = 3;
    private static final String LEAF_PREFIX = "Plant_Leaves";

    private boolean felling;
    private long treesFelled;
    private long blocksFelled;

    public TreeFelling() {
        super(BreakBlockEvent.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final BreakBlockEvent event) {
        if (felling) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TuningConfig config = TuningConfig.resolve(world);
        if (!config.isTreeFelling()) {
            return;
        }
        Vector3i target = event.getTargetBlock();
        int x = target.x;
        int y = target.y;
        int z = target.z;
        int trunkId;
        try {
            trunkId = BlockType.getAssetMap().getIndex(event.getBlockType().getId());
        } catch (Exception e) {
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (!isTrunk(chunkStore, x, y, z, trunkId)) {
            return;
        }

        List<int[]> logs = floodFill(chunkStore, x, y, z, trunkId, config.getTreeMaxBlocks());
        if (logs.size() < MIN_TRUNK) {
            return;
        }

        event.setCancelled(true);
        var instigator = archetypeChunk.getReferenceTo(index);
        felling = true;
        try {
            int done = 0;
            for (int i = 0; i < logs.size(); i++) {
                int[] p = logs.get(i);
                if (breakBlock(store, chunkStore, instigator, event, p[0], p[1], p[2], i == 0)) {
                    done++;
                }
            }
            for (int[] p : floodLeaves(chunkStore, logs, config.getTreeMaxBlocks())) {
                if (breakBlock(store, chunkStore, instigator, event, p[0], p[1], p[2], false)) {
                    done++;
                }
            }
            blocksFelled += done;
            treesFelled++;
        } finally {
            felling = false;
        }
    }

    private static boolean breakBlock(@Nonnull final Store<EntityStore> store,
            @Nonnull final ChunkStore chunkStore, @Nullable final Ref<EntityStore> instigator,
            @Nonnull final BreakBlockEvent event, final int x, final int y, final int z,
            final boolean first) {
        try {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return false;
            }
            BlockHarvestUtils.performBlockBreak(instigator, event.getItemInHand(),
                new Vector3i(x, y, z), first ? SetBlockSettings.NONE : QUIET_REST,
                sectionRef, store, chunkStore.getStore());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTrunk(@Nonnull final ChunkStore chunkStore,
            final int x, final int y, final int z, final int trunkId) {
        int run = 0;
        for (int i = 0; i < TRUNK_SCAN_UP; i++) {
            if (getBlockId(chunkStore, x, y + i, z) != trunkId) {
                break;
            }
            if (++run >= MIN_TRUNK) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static List<int[]> floodFill(@Nonnull final ChunkStore chunkStore,
            final int x, final int y, final int z, final int trunkId, final int max) {
        List<int[]> out = new ArrayList<>(64);
        ArrayDeque<int[]> queue = new ArrayDeque<>(64);
        out.add(new int[]{x, y, z});
        queue.add(new int[]{x, y, z});
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && out.size() < max) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                int nz = cur[2] + d[2];
                if (contains(out, nx, ny, nz) || getBlockId(chunkStore, nx, ny, nz) != trunkId) {
                    continue;
                }
                int[] p = new int[]{nx, ny, nz};
                out.add(p);
                queue.add(p);
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    @Nonnull
    private static List<int[]> floodLeaves(@Nonnull final ChunkStore chunkStore,
            @Nonnull final List<int[]> trunk, final int max) {
        List<int[]> out = new ArrayList<>(64);
        ArrayDeque<int[]> queue = new ArrayDeque<>(64);
        for (int[] t : trunk) {
            queue.add(new int[]{t[0] + 1, t[1], t[2]});
            queue.add(new int[]{t[0] - 1, t[1], t[2]});
            queue.add(new int[]{t[0], t[1], t[2] + 1});
            queue.add(new int[]{t[0], t[1], t[2] - 1});
            queue.add(new int[]{t[0], t[1] + 1, t[2]});
            queue.add(new int[]{t[0], t[1] - 1, t[2]});
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && out.size() < max) {
            int[] cur = queue.poll();
            if (contains(trunk, cur[0], cur[1], cur[2]) || contains(out, cur[0], cur[1], cur[2])) {
                continue;
            }
            if (!isLeaf(getBlockId(chunkStore, cur[0], cur[1], cur[2])) || !nearTrunk(cur, trunk)) {
                continue;
            }
            out.add(cur);
            for (int[] d : dirs) {
                queue.add(new int[]{cur[0] + d[0], cur[1] + d[1], cur[2] + d[2]});
                if (out.size() + queue.size() > max * 4) {
                    queue.clear();
                    break;
                }
            }
        }
        return out;
    }

    private static boolean nearTrunk(@Nonnull final int[] p, @Nonnull final List<int[]> trunk) {
        for (int[] t : trunk) {
            if (Math.abs(p[0] - t[0]) <= LEAF_RADIUS
                    && Math.abs(p[1] - t[1]) <= LEAF_RADIUS
                    && Math.abs(p[2] - t[2]) <= LEAF_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLeaf(final int blockId) {
        if (blockId < 0) {
            return false;
        }
        try {
            var asset = BlockType.getAssetMap().getAsset(blockId);
            return asset != null && asset.getId() != null && asset.getId().startsWith(LEAF_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean contains(@Nonnull final List<int[]> list, final int x, final int y, final int z) {
        for (int[] p : list) {
            if (p[0] == x && p[1] == y && p[2] == z) {
                return true;
            }
        }
        return false;
    }

    private static int getBlockId(@Nonnull final ChunkStore chunkStore, final int x, final int y, final int z) {
        try {
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            if (sectionRef == null || !sectionRef.isValid()) {
                return -1;
            }
            BlockSection section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
            return section == null ? -1 : section.get(x, y, z);
        } catch (Exception e) {
            return -1;
        }
    }

    public long getTreesFelled() {
        return treesFelled;
    }

    public long getBlocksFelled() {
        return blocksFelled;
    }

    public void resetCounters() {
        treesFelled = 0;
        blocksFelled = 0;
    }
}
