package org.tpunn.autoblade.utilities;

import javax.lang.model.element.*;
import java.util.*;

public final class LocationResolver {
    private static final String DEFAULT_LOCATION = "App";

    public static String resolveLocation(TypeElement te) {
        for (AnnotationMirror mirror : te.getAnnotationMirrors()) {
            Element anno = mirror.getAnnotationType().asElement();
            String fq = ((TypeElement) anno).getQualifiedName().toString();

            // If annotated directly with the library-level @Anchored("key") use that string key
            if (fq.equals("org.tpunn.autoblade.annotations.Anchored") || fq.equals("org.tpunn.autoblade.annotations.Seed")) {
                for (java.util.Map.Entry<? extends javax.lang.model.element.ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> ev : mirror.getElementValues().entrySet()) {
                    if (ev.getKey().getSimpleName().contentEquals("value")) {
                        Object v = ev.getValue().getValue();
                        if (v instanceof String s && !s.isEmpty()) {
                            return NamingUtils.toPascalCase(s);
                        }
                    }
                }
                // Presence of @Anchored without a value is invalid, signal caller
                return null;
            }

            if (anno.getSimpleName().toString().equals("Singleton")) return DEFAULT_LOCATION;
        }
        return DEFAULT_LOCATION;
    }

    public static Map<String, List<TypeElement>> groupByAnchor(Set<TypeElement> types) {
        Map<String, List<TypeElement>> map = new LinkedHashMap<>();
        for (TypeElement te : types) {
            String loc = resolveLocation(te);
            String key = (loc == null || loc.isEmpty()) ? DEFAULT_LOCATION : loc;
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(te);
        }
        return map;
    }

    public static String resolveAnchorName(TypeElement te) {
        String loc = resolveLocation(te);
        return loc.equals(DEFAULT_LOCATION) ? "Singleton" : "Auto" + loc + "Anchor";
    }
}