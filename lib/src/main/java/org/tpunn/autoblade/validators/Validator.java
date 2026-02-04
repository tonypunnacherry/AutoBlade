package org.tpunn.autoblade.validators;

import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.LocationResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Validator {
    private final ProcessingEnvironment env;
    public Validator(ProcessingEnvironment env) { this.env = env; }

    public void validate(RoundEnvironment roundEnv) {
        // Only target elements that are intended to be part of the framework
        Set<? extends Element> allManaged = Stream.of(Scoped.class,  Seed.class)
                .flatMap(anno -> roundEnv.getElementsAnnotatedWith(anno).stream())
                .collect(Collectors.toSet());

        for (Element e : allManaged) {
            if (!(e instanceof TypeElement te)) continue;

            boolean hasAnchor = hasAnchorMetaAnnotation(te);
            boolean isImplementation = te.getAnnotation(Scoped.class) != null;

            // Rule 1: If it's an implementation, it MUST implement a business interface
            if (isImplementation && te.getInterfaces().isEmpty()) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "AutoBlade: '" + te.getSimpleName() + "' is managed but must implement a business interface for binding.", te);
            }

            // Rule 2: Validation of seeds
            if (te.getAnnotation(Seed.class) != null && !hasAnchor) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "AutoBlade: Seed '" + te.getSimpleName() + "' must be annotated with an @Anchored to define its scope.", te);
            }
        }
    }

    private boolean hasAnchorMetaAnnotation(TypeElement te) {
        // Older meta-annotation approach removed; presence of an anchor is determined by LocationResolver
        return !LocationResolver.resolveLocation(te).equals("App");
    }
}
