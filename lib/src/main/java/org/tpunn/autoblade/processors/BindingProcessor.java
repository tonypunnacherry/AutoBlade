package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.InterfaceSelector;
import org.tpunn.autoblade.utilities.GeneratedPackageResolver;
import org.tpunn.autoblade.utilities.LocationResolver;
import org.tpunn.autoblade.utilities.FileCollector;
import javax.tools.Diagnostic;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.Transient",
    "org.tpunn.autoblade.annotations.Scoped",
    "org.tpunn.autoblade.annotations.EntryPoint"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class BindingProcessor extends AbstractProcessor {
    private static final String DEFAULT_LOCATION = "App";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        // Collect all managed types
        Set<TypeElement> services = FileCollector.collectManaged(roundEnv,
                Arrays.asList(Transient.class, Scoped.class, EntryPoint.class));

        Map<String, List<TypeElement>> byAnchor = LocationResolver.groupByAnchor(services);

        for (Map.Entry<String, List<TypeElement>> entry : byAnchor.entrySet()) {
            List<TypeElement> list = entry.getValue();
            TypeElement origin = list.stream().filter(te -> te.getAnnotation(Seed.class) != null).findFirst().orElse(list.get(0));
            generateForAnchor(entry.getKey(), list, origin);
        }

        return true;
    }

    private void generateForAnchor(String anchor, List<TypeElement> svcs, TypeElement originating) {
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(Collections.singleton(originating), processingEnv);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "AutoBlade: Generating module for anchor '" + anchor + "' in '" + pkg + "' (" + svcs.size() + " services).");

        TypeSpec module = createModuleSpec(anchor, svcs, pkg);
        writeFileForOrigin(pkg, module, List.of(originating));
    }

    private TypeSpec createModuleSpec(String anchor, List<TypeElement> svcs, String pkg) {
        TypeSpec.Builder mod = TypeSpec.classBuilder(anchor + "AutoModule")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("dagger", "Module"));

        for (TypeElement te : svcs) {
            TypeMirror iface = InterfaceSelector.selectBestInterface(te, processingEnv);
            if (iface == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "AutoBlade: Cannot generate binding for '" + te.getSimpleName() + "' as it could not find a valid interface.", te);
                continue;
            }

            if (iface.toString().equals(te.asType().toString())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "AutoBlade: Implementation '" + te.getSimpleName() + "' cannot be bound to itself. Please expose a distinct interface.", te);
                continue;
            }

            ScopeKind scopeKind;
            try {
                scopeKind = determineScopeKind(te, anchor);
            } catch (IllegalArgumentException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "AutoBlade: " + e.getMessage(), te);
                continue;
            }

            MethodSpec m = createBindMethod(te, iface, scopeKind, pkg);
            if (m != null) mod.addMethod(m);
        }
        return mod.build();
    }

    private MethodSpec createBindMethod(TypeElement impl, TypeMirror iface, ScopeKind scopeKind, String pkg) {
        MethodSpec.Builder bind = MethodSpec.methodBuilder("bind" + impl.getSimpleName())
                .addAnnotation(ClassName.get("dagger", "Binds"))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TypeName.get(iface))
                .addParameter(TypeName.get(impl.asType()), "impl");

        // Based on the scope, add the appropriate annotation
        switch (scopeKind) {
            case NONE: break;
            case SINGLETON: bind.addAnnotation(ClassName.get("javax.inject", "Singleton")); break;
            case ANCHOR: bind.addAnnotation(resolveAnchorAnnotation(impl, pkg)); break;
        }

        return bind.build();
    }

    private ClassName resolveAnchorAnnotation(TypeElement te, String pkg) {
        String loc = LocationResolver.resolveLocation(te);
        if (DEFAULT_LOCATION.equals(loc)) return ClassName.get("javax.inject", "Singleton");
        return ClassName.get(pkg, "Auto" + loc + "Anchor");
    }

    private ScopeKind determineScopeKind(TypeElement te, String anchor) {
        boolean isTransient = te.getAnnotation(Transient.class) != null;
        boolean isEntryPoint = te.getAnnotation(EntryPoint.class) != null;
        boolean isScoped = te.getAnnotation(Scoped.class) != null;
        boolean isSingleton = te.getAnnotation(javax.inject.Singleton.class) != null;

        if (DEFAULT_LOCATION.equals(anchor)) {
            // App-level (Root Component) logic
            if (isScoped) {
                // App services cannot be scoped
                throw new IllegalArgumentException("@Scoped is for subcomponents. Use @Singleton at the App level: " + te.getSimpleName());
            }
            
            // Only apply Singleton if explicitly marked or if it's a non-transient EntryPoint
            if (isTransient) return ScopeKind.NONE;
            if (isSingleton || isEntryPoint) return ScopeKind.SINGLETON;
            
            return ScopeKind.NONE; // Default to unscoped for safety
        } else {
            // Subcomponent logic
            if (isSingleton) {
                throw new IllegalArgumentException("@Singleton cannot be used in a subcomponent (anchor: " + anchor + "). Use @Scoped instead.");
            }
            if (isTransient) return ScopeKind.NONE;
            
            // Every other non-transient anchored service gets the Anchor scope
            return ScopeKind.ANCHOR;
        }
    }

    private void writeFileForOrigin(String pkg, TypeSpec spec, List<TypeElement> origins) {
        try {
            JavaFile.builder(pkg, spec)
                    .build()
                    .writeTo(processingEnv.getFiler());
            
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, 
                    "AutoBlade: Generated " + pkg + "." + spec.name);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                    "AutoBlade: Failed to write " + spec.name + ": " + e.getMessage());
        }
    }

    private enum ScopeKind { NONE, SINGLETON, ANCHOR }
}
