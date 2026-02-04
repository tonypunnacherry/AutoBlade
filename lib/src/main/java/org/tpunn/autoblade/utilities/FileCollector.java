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
            Seed anno = seed.getAnnotation(Seed.class);
            if (anno != null) {
                // Assuming Seed.value() returns the Anchor string or enum name
                map.put(anno.value().toString().toLowerCase(), seed);
            }
        }
        return map;
    }
}
