package org.tpunn.autoblade.registry;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Core algorithms for Repository operations.
 * Encapsulates creation, local lookup, and traversal strategies.
 */
public final class RepoOps {
    private RepoOps() {}

    /**
     * Atomic creation logic.
     * Uses computeIfAbsent for ConcurrentMaps to ensure thread-safety.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> T createAtomic(Map<?, T> cache, Object id, Supplier<T> builder) {
        if (id == null) throw new IllegalArgumentException("ID cannot be null");

        // Use a raw cast to 'Map' to bypass capture #1 extends Object issues
        Map rawCache = (Map) cache;

        if (cache instanceof ConcurrentMap) {
            return (T) ((ConcurrentMap) rawCache).computeIfAbsent(id, k -> builder.get());
        }

        synchronized (cache) {
            T existing = (T) rawCache.get(id);
            if (existing != null) return existing;
            
            T created = builder.get();
            rawCache.put(id, created);
            return created;
        }
    }

    /**
     * Standard local lookup logic.
     */
    public static <T> T lookup(Map<Object, T> cache, Object id) {
        if (id == null) return null;
        return cache.get(id);
    }

    /**
     * Performs a deep search via the Global Registry and returns the first match.
     */
    public static <T> Optional<T> findFirst(BladeRegistry registry, Object id) {
        return registry.find(id);
    }

    /**
     * Performs a deep search via the Global Registry and returns all matches.
     */
    public static <T> Set<T> findAll(BladeRegistry registry, Object id, Class<T> type) {
        return registry.findAll(id, type);
    }

    /**
     * Hierarchical traversal logic for nested lookups.
     * e.g., Finding a project within a specific user.
     */
    public static <T> Optional<T> traverse(BladeRegistry registry, Object parentId, Object childId, Class<T> type) {
        return registry.findInParent(parentId, childId, type);
    }
}
