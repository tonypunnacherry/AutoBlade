package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.inject.Singleton;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BindingProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> svcs = FileCollector.collectManaged(roundEnv, Arrays.asList(Transient.class, Scoped.class, Singleton.class, AutoBuilder.class));
        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        Set<TypeElement> contracts = FileCollector.collectManaged(roundEnv, Blade.class);

        if (svcs.isEmpty() && repos.isEmpty() && contracts.isEmpty()) return true;

        Set<TypeElement> allElements = new HashSet<>(svcs);
        allElements.addAll(repos);
        allElements.addAll(contracts);
        
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(allElements, processingEnv);

        Map<String, List<TypeElement>> svcsByAnchor = LocationResolver.groupByAnchor(svcs);
        Map<String, List<TypeElement>> reposByAnchor = LocationResolver.groupByAnchor(repos);

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

        // Repository Bindings
        for (TypeElement repo : repos) {
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

        for (TypeElement te : svcs) {
            TypeMirror ifaceMirror = InterfaceSelector.selectBestInterface(te, processingEnv);
            if (ifaceMirror == null) continue;
            TypeName iface = TypeName.get(ifaceMirror);
            TypeName impl = TypeName.get(te.asType());

            if (BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.AutoBuilder")) {
                iface = InterfaceSelector.selectBuilderInterface(pkg, ifaceMirror, te, processingEnv); // The Builder interface
                impl = InterfaceSelector.selectBuilderInterface(pkg, te.asType(), te, processingEnv); // The generated Builder impl
            }

            if (iface == null || iface.equals(impl)) continue;
            
            System.out.println("Binding " + impl + " to " + iface + " in anchor: " + anchor);
            
            MethodSpec.Builder mb = MethodSpec.methodBuilder("bind" + te.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(iface)
                    .addParameter(impl, "impl");

            Optional<? extends AnnotationMirror> strategyMirror = BindingUtils.getStrategyMirror(te);

            // 1. Handle Strategy-specific annotations
            if (strategyMirror.isPresent()) {
                mb.addAnnotation(ClassName.get("dagger.multibindings", "IntoMap"));
                
                AnnotationMirror mirror = strategyMirror.get();
                if (mirror == null || mirror.getAnnotationType() == null || mirror.getAnnotationType().asElement() == null) {
                    continue; // Defensive check
                }
                String keyClassName = mirror.getAnnotationType().asElement().getSimpleName() + "Key";
                
                // Extract Enum constant safely
                AnnotationValue enumVal = mirror.getElementValues().values().iterator().next();
                if (enumVal == null) continue; // Defensive check
                if (enumVal.getValue() instanceof VariableElement enumConstant) {
                    mb.addAnnotation(AnnotationSpec.builder(ClassName.get(pkg, keyClassName))
                            .addMember("value", "$T.$L", TypeName.get(enumConstant.asType()), enumConstant.getSimpleName())
                            .build());
                }
            }

            // 2. UNIFIED SCOPING: Add the anchor scope exactly once if not transient
            if (!BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.Transient")) {
                String scopeName = LocationResolver.resolveAnchorName(te);
                ClassName scopeAnno = "Singleton".equals(scopeName) 
                        ? ClassName.get("javax.inject", "Singleton") 
                        : ClassName.get(pkg, scopeName);
                
                mb.addAnnotation(scopeAnno);
            }

            mod.addMethod(mb.build());
        }

        try {
            JavaFile.builder(pkg, mod.build()).build().writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }
}
