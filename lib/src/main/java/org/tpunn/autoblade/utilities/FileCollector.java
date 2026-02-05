package org.tpunn.autoblade.utilities;

import org.tpunn.autoblade.annotations.Seed;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;
import java.util.*;

public final class FileCollector {

    public static Set<TypeElement> collectManaged(RoundEnvironment roundEnv, Collection<Class<? extends Annotation>> annotations) {
        Set<TypeElement> set = new LinkedHashSet<>();
        for (Class<? extends Annotation> annoClass : annotations) {
            roundEnv.getElementsAnnotatedWith(annoClass).forEach(e -> {
                if (e instanceof TypeElement te) set.add(te);
            });
        }
        return set;
    }

    public static Set<TypeElement> collectManaged(RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
        return collectManaged(roundEnv, Collections.singleton(annotation));
    }

    /**
     * Maps Anchors to their corresponding Seed elements.
     * This allows a Repository to look up the ID logic for ANY blade it needs to find.
     */
    public static Map<String, TypeElement> mapAnchorsToSeeds(RoundEnvironment roundEnv) {
        Map<String, TypeElement> map = new HashMap<>();
        Set<TypeElement> seeds = collectManaged(roundEnv, Seed.class);
        for (TypeElement seed : seeds) {
            seed.getAnnotationMirrors().stream()
                .filter(mirror -> mirror != null && mirror.getAnnotationType().toString().equals(Seed.class.getName()))
                .findFirst()
                .ifPresent(mirror -> {
                    if (mirror == null) return;
                    mirror.getElementValues().entrySet().stream()
                        .filter(entry -> {
                            var key = entry.getKey();
                            return key != null && key.getSimpleName().contentEquals("value");
                        })
                        .findFirst()
                        .ifPresent(entry -> {
                            var val = entry.getValue();
                            if (val == null) return;
                            map.put(val.getValue().toString().toLowerCase(), seed);
                        });
                });
        }
        return map;
    }
}
