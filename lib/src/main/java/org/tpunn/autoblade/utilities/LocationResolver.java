package org.tpunn.autoblade.utilities;

import javax.lang.model.element.*;
import java.util.*;

public final class LocationResolver {
    private static final String DEFAULT_LOCATION = "App";

    public static String resolveLocation(TypeElement te) {
        for (AnnotationMirror mirror : te.getAnnotationMirrors()) {
            if (mirror == null) continue;
            Element anno = mirror.getAnnotationType().asElement();
            String fq = ((TypeElement) anno).getQualifiedName().toString();

            // If annotated directly with the library-level @Anchored("key") use that string key
            if (fq.equals("org.tpunn.autoblade.annotations.Anchored") || fq.equals("org.tpunn.autoblade.annotations.Seed")) {
                for (java.util.Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> ev : mirror.getElementValues().entrySet()) {
                    var key = ev.getKey();
                    var val = ev.getValue();
                    if (key == null) continue;
                    if (val == null) continue;
                    if (key.getSimpleName().contentEquals("value")) {
                        Object v = val.getValue();
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