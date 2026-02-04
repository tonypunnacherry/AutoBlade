package org.tpunn.autoblade.utilities;

import javax.lang.model.element.*;
import java.util.*;

public final class LocationResolver {
    private static final String DEFAULT_LOCATION = "App";

    public static String resolveLocation(TypeElement te) {
        if (te == null) return DEFAULT_LOCATION;

        for (AnnotationMirror mirror : te.getAnnotationMirrors()) {
            if (mirror == null) continue;
            TypeElement anno = (TypeElement) mirror.getAnnotationType().asElement();
            String fq = anno.getQualifiedName().toString();

            // Check for our library annotations
            if (fq.equals("org.tpunn.autoblade.annotations.Anchored") 
                    || fq.equals("org.tpunn.autoblade.annotations.Seed")
                    || fq.equals("org.tpunn.autoblade.annotations.Blade")) {
                
                String value = getAnnotationValue(mirror, "value");
                
                // FIX: If the annotation is present but empty/null, it's the Root (App)
                if (value == null || value.trim().isEmpty()) {
                    return DEFAULT_LOCATION;
                }
                
                return NamingUtils.toPascalCase(value);
            }

            // Standard JSR-330 Singleton defaults to App
            if (fq.equals("javax.inject.Singleton") || fq.equals("jakarta.inject.Singleton")) {
                return DEFAULT_LOCATION;
            }
        }
        return DEFAULT_LOCATION;
    }

    /**
     * Safely extracts a string value from an annotation mirror.
     */
    private static String getAnnotationValue(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                Object val = entry.getValue().getValue();
                return val instanceof String s ? s : null;
            }
        }
        return null;
    }

    public static Map<String, List<TypeElement>> groupByAnchor(Set<TypeElement> types) {
        Map<String, List<TypeElement>> map = new LinkedHashMap<>();
        for (TypeElement te : types) {
            String loc = resolveLocation(te);
            // Safety: fallback to DEFAULT_LOCATION if logic above returned null
            String key = (loc == null || loc.isEmpty()) ? DEFAULT_LOCATION : loc;
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(te);
        }
        return map;
    }

    public static String resolveAnchorName(TypeElement te) {
        String loc = resolveLocation(te);
        return DEFAULT_LOCATION.equals(loc) ? "Singleton" : "Auto" + loc + "Anchor";
    }
}
