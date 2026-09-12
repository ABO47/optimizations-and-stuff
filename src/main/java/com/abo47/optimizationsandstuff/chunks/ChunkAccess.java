package com.abo47.optimizationsandstuff.chunks;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.abo47.optimizationsandstuff.config.TuningConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Single choke point for mod chunk access. Tick-thread callers use the async
 * store fetch path, never blocking calls.
 */
public final class ChunkAccess {

    private ChunkAccess() {
    }

    public static boolean isTickThread(final World world) {
        return world.isInThread();
    }

    public static boolean useAsyncPaths(final World world) {
        return TuningConfig.resolve(world).isAsyncFetch();
    }

    public static CompletableFuture<Ref<ChunkStore>> getChunkAsync(final World world, final long chunkIndex) {
        return world.getChunkStore().getChunkReferenceAsync(chunkIndex);
    }
}
