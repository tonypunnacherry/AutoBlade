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

        // 1. Collect all types managed by the framework
        Set<TypeElement> svcs = FileCollector.collectManaged(roundEnv, Arrays.asList(Transient.class, Scoped.class, Anchored.class));
        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        Set<TypeElement> contracts = FileCollector.collectManaged(roundEnv, Blade.class);

        if (svcs.isEmpty() && repos.isEmpty() && contracts.isEmpty()) return true;

        // 2. Resolve target package and group elements by their intended Dagger Anchor
        Set<TypeElement> allElements = new HashSet<>(svcs);
        allElements.addAll(repos);
        allElements.addAll(contracts);
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(allElements, processingEnv);

        Map<String, List<TypeElement>> svcsByAnchor = LocationResolver.groupByAnchor(svcs);
        Map<String, List<TypeElement>> reposByAnchor = LocationResolver.groupByAnchor(repos);

        // Ensure we always have an "App" anchor for the root module
        Set<String> allAnchors = new HashSet<>();
        allAnchors.addAll(svcsByAnchor.keySet());
        allAnchors.addAll(reposByAnchor.keySet());
        allAnchors.add("App");

        for (String anchor : allAnchors) {
            generateModule(
                pkg, 
                anchor, 
                svcsByAnchor.getOrDefault(anchor, Collections.emptyList()), 
                reposByAnchor.getOrDefault(anchor, Collections.emptyList()), 
                contracts
            );
        }
        return true;
    }

    private void generateModule(String pkg, String anchor, List<TypeElement> svcs, List<TypeElement> repos, Set<TypeElement> contracts) {
        AnnotationSpec.Builder moduleAnno = AnnotationSpec.builder(ClassName.get("dagger", "Module"));

        // If this is the App module, it must declare subcomponents for every Blade
        if ("App".equalsIgnoreCase(anchor)) {
            List<String> subs = contracts.stream()
                .filter(c -> !"App".equalsIgnoreCase(LocationResolver.resolveLocation(c)))
                .map(c -> LocationResolver.resolveLocation(c) + "Blade_Auto.class")
                .collect(Collectors.toList());
            
            if (!subs.isEmpty()) {
                moduleAnno.addMember("subcomponents", "{$L}", String.join(", ", subs));
            }
        }

        TypeSpec.Builder mod = TypeSpec.interfaceBuilder(anchor + "AutoModule")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(moduleAnno.build());

        // 3. Bind Repositories to this specific Anchor
        for (TypeElement repo : repos) {
            // Find the interface the abstract class implements (e.g., TeamRepository)
            // If the repo is already an interface, use it directly.
            TypeName contractType = TypeName.get(repo.asType());
            if (repo.getKind() == ElementKind.CLASS && !repo.getInterfaces().isEmpty()) {
                contractType = TypeName.get(repo.getInterfaces().get(0));
            }

            mod.addMethod(MethodSpec.methodBuilder("bind" + repo.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(contractType)
                    .addParameter(ClassName.get(pkg, repo.getSimpleName() + "_Repo"), "impl")
                    .build());
        }

        // 4. Bind Services to this specific Anchor
        for (TypeElement te : svcs) {
            var iface = InterfaceSelector.selectBestInterface(te, processingEnv);
            if (iface == null || processingEnv.getTypeUtils().isSameType(iface, te.asType())) continue;

            MethodSpec.Builder mb = MethodSpec.methodBuilder("bind" + te.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(TypeName.get(iface))
                    .addParameter(TypeName.get(te.asType()), "impl");

            // Apply Scope if not Transient
            if (!hasMirror(te, "org.tpunn.autoblade.annotations.Transient")) {
                String scopeName = LocationResolver.resolveAnchorName(te);
                mb.addAnnotation("Singleton".equals(scopeName) ? 
                    ClassName.get("javax.inject", "Singleton") : ClassName.get(pkg, scopeName));
            }
            mod.addMethod(mb.build());
        }

        try {
            JavaFile.builder(pkg, mod.build()).build().writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }

    private boolean hasMirror(TypeElement te, String fq) {
        return te.getAnnotationMirrors().stream()
                .anyMatch(m -> m != null && ((TypeElement)m.getAnnotationType().asElement()).getQualifiedName().contentEquals(fq));
    }
}
