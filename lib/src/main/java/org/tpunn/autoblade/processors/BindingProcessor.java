package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BindingProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> svcs = FileCollector.collectManaged(roundEnv, Arrays.asList(Transient.class, Scoped.class, Anchored.class));
        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        Set<TypeElement> allContracts = FileCollector.collectManaged(roundEnv, Blade.class);

        if (svcs.isEmpty() && repos.isEmpty() && allContracts.isEmpty()) return true;

        // Resolve package based on all elements involved in bindings
        Set<TypeElement> allElements = new HashSet<>(svcs);
        allElements.addAll(repos);
        allElements.addAll(allContracts);
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(allElements, processingEnv);

        Map<String, List<TypeElement>> svcsByAnchor = LocationResolver.groupByAnchor(svcs);
        Set<String> anchors = new HashSet<>(svcsByAnchor.keySet());
        anchors.add("App");

        for (String anchor : anchors) {
            generateModule(pkg, anchor, svcsByAnchor.getOrDefault(anchor, Collections.emptyList()), repos, allContracts);
        }
        return true;
    }

    private void generateModule(String pkg, String anchor, List<TypeElement> svcs, Set<TypeElement> repos, Set<TypeElement> contracts) {
        // 1. Start a single Module annotation builder
        AnnotationSpec.Builder moduleAnno = AnnotationSpec.builder(ClassName.get("dagger", "Module"));

        if ("App".equalsIgnoreCase(anchor)) {
            // Add subcomponents to the SAME annotation builder
            List<String> subs = contracts.stream()
                .filter(c -> !"App".equalsIgnoreCase(LocationResolver.resolveLocation(c)))
                .map(c -> LocationResolver.resolveLocation(c) + "Blade_Auto.class")
                .collect(Collectors.toList());
            
            if (!subs.isEmpty()) {
                moduleAnno.addMember("subcomponents", "{$L}", String.join(", ", subs));
            }
        }

        // 2. Build the interface with the single consolidated annotation
        TypeSpec.Builder mod = TypeSpec.interfaceBuilder(anchor + "AutoModule")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(moduleAnno.build());

        // 3. Bind Repositories (Only in AppAutoModule)
        if ("App".equalsIgnoreCase(anchor)) {
            for (TypeElement repo : repos) {
                mod.addMethod(MethodSpec.methodBuilder("bind" + repo.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(TypeName.get(repo.asType()))
                    .addParameter(ClassName.get(pkg, repo.getSimpleName() + "_Repo"), "impl")
                    .build());
            }
        }

        // 4. Bind Services (Transient/Scoped)
        for (TypeElement te : svcs) {
            var iface = InterfaceSelector.selectBestInterface(te, processingEnv);
            if (iface == null || processingEnv.getTypeUtils().isSameType(iface, te.asType())) continue;

            MethodSpec.Builder mb = MethodSpec.methodBuilder("bind" + te.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(TypeName.get(iface))
                    .addParameter(TypeName.get(te.asType()), "impl");

            if (!hasMirror(te, "org.tpunn.autoblade.annotations.Transient")) {
                String scope = LocationResolver.resolveAnchorName(te);
                mb.addAnnotation("Singleton".equals(scope) ? ClassName.get("javax.inject", "Singleton") : ClassName.get(pkg, scope));
            }
            mod.addMethod(mb.build());
        }

        try { 
            JavaFile.builder(pkg, mod.build()).build().writeTo(processingEnv.getFiler()); 
        } catch (IOException ignored) {}
    }

    private boolean hasMirror(TypeElement te, String fq) {
        return te.getAnnotationMirrors().stream().anyMatch(m -> ((TypeElement)m.getAnnotationType().asElement()).getQualifiedName().contentEquals(fq));
    }
}
