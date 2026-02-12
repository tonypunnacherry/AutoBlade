package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class BindingProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        // 1. Collect all types
        Set<TypeElement> svcs = FileCollector.collectManaged(roundEnv, Arrays.asList(
            Transient.class, Scoped.class, javax.inject.Singleton.class, AutoBuilder.class, AutoFactory.class));
        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        Set<TypeElement> contracts = FileCollector.collectManaged(roundEnv, Blade.class);

        // Filter out Blade contracts from the "bindable services" list to avoid circular dependencies
        svcs.removeIf(te -> BindingUtils.hasMirror(te, Blade.class.getName()));

        if (svcs.isEmpty() && repos.isEmpty() && contracts.isEmpty()) return true;

        // 2. Grouping
        Map<String, List<TypeElement>> svcsByAnchor = LocationResolver.groupByAnchor(svcs);
        Map<String, List<TypeElement>> reposByAnchor = LocationResolver.groupByAnchor(repos);

        Set<String> allAnchors = new HashSet<>(svcsByAnchor.keySet());
        allAnchors.addAll(reposByAnchor.keySet());
        allAnchors.add("App");

        for (String anchor : allAnchors) {
            generateModule(anchor, 
                svcsByAnchor.getOrDefault(anchor, Collections.emptyList()), 
                reposByAnchor.getOrDefault(anchor, Collections.emptyList()), 
                contracts);
        }
        return true;
    }

    private void generateModule(String anchor, List<TypeElement> svcs, List<TypeElement> repos, Set<TypeElement> contracts) {
        TypeElement owner = contracts.stream()
                .filter(c -> anchor.equalsIgnoreCase(LocationResolver.resolveLocation(c)))
                .findFirst()
                .orElse(null);

        // Root/App logic
        String pkg = (owner != null) 
                ? GeneratedPackageResolver.getPackage(owner, processingEnv)
                : GeneratedPackageResolver.computeGeneratedPackage(contracts, processingEnv);
        
        // Ensure pkg isn't empty to avoid default package issues
        if (pkg == null || pkg.isEmpty()) pkg = "org.tpunn.autoblade";

        ClassName moduleCn = ClassName.get(pkg, anchor + "AutoModule");
        TypeSpec.Builder modBuilder = TypeSpec.interfaceBuilder(moduleCn)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("dagger", "Module"));

        // 3. Subcomponents (Only for App Module)
        if ("App".equalsIgnoreCase(anchor)) {
            List<ClassName> subTypes = contracts.stream()
                .filter(c -> !"App".equalsIgnoreCase(LocationResolver.resolveLocation(c)))
                .map(c -> ClassName.get(c).peerClass(LocationResolver.resolveLocation(c) + "Blade_Auto"))
                .collect(Collectors.toList());
            
            if (!subTypes.isEmpty()) {
                CodeBlock subList = subTypes.stream()
                    .map(type -> CodeBlock.of("$T.class", type))
                    .collect(CodeBlock.joining(", ", "{", "}"));
                moduleAnno(modBuilder).addMember("subcomponents", subList);
            }
        }

        // 4. Repository Bindings
        for (TypeElement repo : repos) {
            // Find the interface (e.g., TeamRepository)
            TypeName ifaceType = TypeName.get(repo.asType());
            if (repo.getKind() == ElementKind.CLASS && !repo.getInterfaces().isEmpty()) {
                ifaceType = TypeName.get(repo.getInterfaces().get(0));
            }

            ClassName impl = ClassName.get(repo).peerClass(repo.getSimpleName() + "_Repo");
            generateBinding(modBuilder, repo, ifaceType, impl, "Repo", Optional.empty(), owner);
        }

        // 5. Services
        for (TypeElement te : svcs) {
            ClassName teCn = ClassName.get(te);
            TypeMirror bestIface = InterfaceSelector.selectBestInterface(te, processingEnv);
            if (bestIface == null) continue;

            Optional<? extends AnnotationMirror> strategy = BindingUtils.getStrategyMirror(te);
            boolean isFactory = BindingUtils.hasMirror(te, AutoFactory.class.getName());
            boolean isBuilder = BindingUtils.hasMirror(te, AutoBuilder.class.getName());

            if (!isFactory && !isBuilder) {
                generateBinding(modBuilder, te, TypeName.get(bestIface), TypeName.get(te.asType()), "", strategy, owner);
            }

            if (isFactory) {
                String shared = FactoryNaming.resolveName(te, processingEnv, strategy.isPresent() ? "?" : AutoFactory.class.getName(), "Factory");
                String actual = FactoryNaming.resolveName(te, processingEnv, AutoFactory.class.getName(), "Factory");
                if (shared != null && actual != null && (!shared.equals(actual) || strategy.isPresent())) {
                    generateBinding(modBuilder, te, teCn.peerClass(shared), teCn.peerClass(actual), "Factory", strategy, owner);
                }
            }

            if (isBuilder) {
                String shared = FactoryNaming.resolveName(te, processingEnv, AutoBuilder.class.getName(), "Builder");
                if (shared != null) {
                    generateBinding(modBuilder, te, teCn.peerClass(shared), teCn.peerClass(shared + "Impl"), "Builder", Optional.empty(), owner);
                }
            }
        }

        try {
            JavaFile.builder(pkg, modBuilder.build()).skipJavaLangImports(true).build().writeTo(processingEnv.getFiler());
        } catch (FilerException ignored) {
            // This prevents duplicate file creation errors across rounds
        } catch (IOException ignored) {}
    }

    private void generateBinding(TypeSpec.Builder mod, TypeElement origin, TypeName iface, TypeName impl, 
                                 String suffix, Optional<? extends AnnotationMirror> strategy, TypeElement owner) {
        if (iface.equals(impl) && suffix.isEmpty()) return;

        MethodSpec.Builder mb = MethodSpec.methodBuilder("bind" + origin.getSimpleName() + suffix)
                .addAnnotation(ClassName.get("dagger", "Binds"))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(iface)
                .addParameter(impl, "impl");

        if (strategy.isPresent()) {
            mb.addAnnotation(ClassName.get("dagger.multibindings", "IntoMap"));
            TypeElement keyDef = (TypeElement) strategy.get().getAnnotationType().asElement();
            AnnotationValue enumVal = strategy.get().getElementValues().values().iterator().next();
            mb.addAnnotation(AnnotationSpec.builder(ClassName.get(keyDef).peerClass(keyDef.getSimpleName() + "Key"))
                    .addMember("value", "$T.$L", TypeName.get(((VariableElement)enumVal.getValue()).asType()), 
                            ((VariableElement)enumVal.getValue()).getSimpleName())
                    .build());
        }

        String rawLoc = LocationResolver.resolveLocation(origin);
        if (!BindingUtils.hasMirror(origin, Transient.class.getName())) {
            if ("App".equalsIgnoreCase(rawLoc) || "Singleton".equalsIgnoreCase(rawLoc)) {
                mb.addAnnotation(ClassName.get("javax.inject", "Singleton"));
            } else if (owner != null) {
                mb.addAnnotation(ClassName.get(owner).peerClass("Auto" + NamingUtils.toPascalCase(rawLoc) + "Anchor"));
            }
        }
        mod.addMethod(mb.build());
    }

    private AnnotationSpec.Builder moduleAnno(TypeSpec.Builder mod) {
        return AnnotationSpec.builder(ClassName.get("dagger", "Module"));
    }
}
