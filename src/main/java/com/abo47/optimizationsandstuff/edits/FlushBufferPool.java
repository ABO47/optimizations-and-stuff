package com.abo47.optimizationsandstuff.edits;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Bounded tick-thread-only pool. Borrow never allocates on hit; overflow
 * hands out a fresh instance and drops it on release instead of growing.
 */
public class FlushBufferPool<T> {

    private final ArrayList<T> free;
    private final Supplier<T> factory;
    private final int capacity;
    private long hits;
    private long misses;

    public FlushBufferPool(final int capacity, @Nonnull final Supplier<T> factory) {
        this.capacity = capacity;
        this.factory = factory;
        this.free = new ArrayList<>(capacity);
    }

    @Nonnull
    public T borrow() {
        int last = free.size() - 1;
        if (last >= 0) {
            hits++;
            return free.remove(last);
        }
        misses++;
        return factory.get();
    }

    public void release(@Nonnull final T item) {
        if (free.size() < capacity) {
            free.add(item);
        }
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public void resetCounters() {
        hits = 0;
        misses = 0;
    }
}
