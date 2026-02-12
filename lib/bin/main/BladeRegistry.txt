/* $PACKAGE_HOLDER$ */
package org.tpunn.autoblade.registry;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class BladeRegistry {
    private final ConcurrentMap<Object, BladeNode> index = new ConcurrentHashMap<>();

    @Inject
    public BladeRegistry() {}

    /** Stitching logic: Connects child to parent for graph traversal */
    public void register(Object id, Object instance, String parentId) {
        BladeNode node = index.computeIfAbsent(id, BladeNode::new);
        node.instance = new WeakReference<>(instance);
        if (parentId != null) {
            BladeNode parent = index.computeIfAbsent(parentId, BladeNode::new);
            parent.children.add(node);
        }
    }

    /** Deep Search: O(1) Find First */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(Object id) {
        BladeNode node = index.get(id);
        if (node == null || node.instance == null) return Optional.empty();
        Object instance = node.instance.get();
        if (instance == null) {
            index.remove(id);
            return Optional.empty();
        }
        return Optional.of((T) instance);
    }

    /** Deep Search: Aggregated Set */
    public <T> Set<T> findAll(Object id, Class<T> type) {
        return find(id).filter(type::isInstance).map(type::cast)
                .map(Collections::singleton).orElse(Collections.emptySet());
    }

    /** Hierarchical Lookup: Find by specific path */
    public <T> Optional<T> findInParent(Object parentId, Object childId, Class<T> type) {
        BladeNode parent = index.get(parentId);
        if (parent == null) return Optional.empty();
        return parent.children.stream()
            .filter(n -> n.id.equals(childId))
            .map(n -> n.instance.get())
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst();
    }

    private static class BladeNode {
        final Object id;
        volatile WeakReference<Object> instance;
        final Set<BladeNode> children = ConcurrentHashMap.newKeySet();
        BladeNode(Object id) { this.id = id; }
    }
}
