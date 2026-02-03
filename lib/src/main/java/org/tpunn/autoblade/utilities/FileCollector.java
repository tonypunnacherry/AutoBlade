package org.tpunn.autoblade.utilities;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

import java.lang.annotation.Annotation;
import java.util.*;

public final class FileCollector {
    /**
     * Collects types annotated with any of the provided annotations.
     * Accepts a collection to avoid varargs-related heap pollution warnings.
     */     
    public static Set<TypeElement> collectManaged(RoundEnvironment roundEnv, Collection<Class<? extends Annotation>> annotations) {
        Set<TypeElement> set = new LinkedHashSet<>();
        for (Class<? extends Annotation> annoClass : annotations) {
            roundEnv.getElementsAnnotatedWith(annoClass).forEach(e -> set.add((TypeElement) e));
        }
        return set;
    }

    /** Convenience overload for a single annotation class. */
    public static Set<TypeElement> collectManaged(RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
        return collectManaged(roundEnv, Collections.singleton(annotation));
    }
}