/* $PACKAGE_HOLDER$ */
package org.tpunn.autoblade.registry;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class BladeRegistry {
    private final ConcurrentMap<String, BladeNode> index = new ConcurrentHashMap<>();

    @Inject
    public BladeRegistry() {}

    /** Stitching logic: Connects child to parent for graph traversal */
    public void register(String id, Object instance, String parentId) {
        BladeNode node = index.computeIfAbsent(id, BladeNode::new);
        node.instance = new WeakReference<>(instance);
        if (parentId != null) {
            BladeNode parent = index.computeIfAbsent(parentId, BladeNode::new);
            parent.children.add(node);
        }
    }

    /** Deep Search: O(1) Find First */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(String id) {
        BladeNode node = index.get(id);
        if (node == null || node.instance == null) return Optional.empty();
        T blade = (T) node.instance.get();
        if (blade == null) {
            index.remove(id); 
            return Optional.empty();
        }
        return Optional.of(blade);
    }

    /** Deep Search: Aggregated Set */
    public <T> Set<T> findAll(String id, Class<T> type) {
        return find(id).filter(type::isInstance).map(type::cast)
                .map(Collections::singleton).orElse(Collections.emptySet());
    }

    /** Hierarchical Lookup: Find by specific path */
    public <T> Optional<T> findInParent(String parentId, String childId, Class<T> type) {
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
        final String id;
        volatile WeakReference<Object> instance;
        final Set<BladeNode> children = ConcurrentHashMap.newKeySet();
        BladeNode(String id) { this.id = id; }
    }
}
