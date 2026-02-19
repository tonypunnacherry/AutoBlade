package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.Repository;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.FileCollector;
import org.tpunn.autoblade.utilities.GeneratedPackageResolver;
import org.tpunn.autoblade.utilities.LocationResolver;
import org.tpunn.autoblade.utilities.NamingUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates repository implementations with automatic caching.
 * Uses Peer-Packaging and Nested Class imports to resolve Dagger classpath errors.
 */
public class RepositoryProcessor extends AbstractProcessor {

    private boolean registryGenerated = false;
    private Map<String, TypeElement> anchorToSeedMap = new HashMap<>();

    public void setAnchorMap(Map<String, TypeElement> map) {
        this.anchorToSeedMap = map;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        if (repos.isEmpty()) return true;

        for (TypeElement repo : repos) {
            // Generate Peer-to-Peer: lives in the same package as the interface
            String pkg = GeneratedPackageResolver.getPackage(repo, processingEnv);
            
            if (!registryGenerated) {
                generateBladeRegistry(pkg, repo);
                generateRepoOps(pkg, repo);
                registryGenerated = true;
            }

            generateRepoImpl(repo, pkg);
        }
        return true;
    }

    private void generateRepoImpl(TypeElement repo, String pkg) {
        String bladeBase = BindingUtils.parseBladeNameFromRepo(repo);
        String implName = repo.getSimpleName() + "_Repo";
        
        // 1. Identify the Blade's package (Assumed to be project root or sibling)
        // If the Blade is in 'org.tpunn.autoblade' and Repo is in 'org.tpunn.autoblade.repos'
        // we need to find the correct root pkg.
        String bladePkg = pkg.contains(".repos") ? pkg.substring(0, pkg.lastIndexOf(".repos")) : pkg;

        // 2. Build the ClassName for the AutoBlade
        ClassName autoBladeType = ClassName.get(bladePkg, bladeBase + "Blade_Auto");
        
        // 3. FIX: Use nestedClass to force an import of the outer class.
        // This stops the "package PlayerBlade_Auto does not exist" error.
        ClassName builderType = autoBladeType.nestedClass("Builder");
        TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), builderType);

        String anchor = LocationResolver.resolveLocation(repo);
        AnnotationSpec scopeAnnotation;
        if ("App".equalsIgnoreCase(anchor)) {
            // Root level always uses Singleton
            scopeAnnotation = AnnotationSpec.builder(ClassName.get("javax.inject", "Singleton")).build();
        } else {
            // Anchored repos MUST use the specific anchor scope to prevent Root-level instantiation
            String scopeName = "Auto" + NamingUtils.toPascalCase(anchor) + "Anchor";
            // Usually, the scope is generated in the same package as the Blade_Auto
            scopeAnnotation = AnnotationSpec.builder(ClassName.get(bladePkg, scopeName)).build();
        }

        TypeSpec.Builder builder = TypeSpec.classBuilder(implName)
                .addOriginatingElement(repo)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(scopeAnnotation)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "all").build());

        if (repo.getKind() == ElementKind.INTERFACE) {
            builder.addSuperinterface(TypeName.get(repo.asType()));
        } else {
            builder.superclass(TypeName.get(repo.asType()));
        }

        ClassName registryType = ClassName.get(pkg, "BladeRegistry");
        ClassName repoOps = ClassName.get(pkg, "RepoOps");

        builder.addField(registryType, "registry", Modifier.PRIVATE, Modifier.FINAL)
               .addField(providerType, "builderProvider", Modifier.PRIVATE, Modifier.FINAL);

        builder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(Inject.class)
                .addParameter(registryType, "registry")
                .addParameter(providerType, "builderProvider")
                .addStatement("this.registry = registry")
                .addStatement("this.builderProvider = builderProvider")
                .build());

        boolean isConcurrent = BindingUtils.hasAnnotation(repo, "org.tpunn.autoblade.annotations.Concurrent");
        
        for (Element e : repo.getEnclosedElements()) {
            if (e == null || e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement m = (ExecutableElement) e;
            
            if (m.getModifiers().contains(Modifier.ABSTRACT)) {
                processMethod(builder, m, bladeBase, isConcurrent, repoOps);
            }
        }

        writeFile(repo, pkg, builder.build());
    }

    private void processMethod(TypeSpec.Builder builder, ExecutableElement m, String repoBladeBase, boolean isConcurrent, ClassName repoOps) {
        if (m.getParameters().isEmpty()) return;
        
        VariableElement param = m.getParameters().get(0);
        if (param == null) return;
        TypeMirror paramType = param.asType();
        String paramName = param.getSimpleName().toString();
        MethodSpec.Builder mb = MethodSpec.overriding(m);

        // 1. Identify what we are looking for (Blade -> Anchor -> Seed)
        TypeName targetBlade = BindingUtils.extractBladeType(m);
        String targetAnchor = BindingUtils.parseAnchorFromBladeName(targetBlade).toLowerCase();
        TypeElement targetSeed = anchorToSeedMap.get(targetAnchor);

        // 2. Resolve the ID Schema for that Seed
        TypeMirror targetIdType = BindingUtils.resolveIdType(targetSeed);
        String accessor = BindingUtils.resolveIdAccessor(targetSeed);

        // 3. Confirm if the param is good enough already
        boolean isIdParam = processingEnv.getTypeUtils().isSameType(paramType, targetIdType);
        
        // idRef: "user.userId()" OR "id" OR "user.id"
        String idRef = isIdParam ? paramName : (accessor.isEmpty() ? paramName : paramName + "." + accessor);

        if (BindingUtils.hasAnnotation(m, "org.tpunn.autoblade.annotations.Create")) {
            ensureCacheField(builder, m.getReturnType(), TypeName.get(targetIdType), repoBladeBase, isConcurrent);

            CodeBlock lambdaBody = CodeBlock.builder()
                .add("var blade = builderProvider.get().seed($L).build();\n", paramName)
                .add("registry.register($L, blade, null);\n", idRef)
                .add("return blade;")
                .build();

            // Raw Map cast to solve CAP#2 generic capture errors
            mb.addStatement("return $T.createAtomic(($T)$L, $L, () -> {\n$L\n})", 
                    repoOps, Map.class, repoBladeBase.toLowerCase() + "Cache", idRef, lambdaBody);            

        } else if (BindingUtils.hasAnnotation(m, "org.tpunn.autoblade.annotations.Lookup")) {
            TypeName returnType = TypeName.get(m.getReturnType());
            if (returnType.toString().contains("java.util.Optional")) {
                mb.addStatement("return registry.<$T>find($L)", targetBlade, idRef);
            } else if (returnType.toString().contains("java.util.Set") || returnType.toString().contains("java.util.List")) {
                mb.addStatement("return registry.findAll($L, $T.class)", idRef, targetBlade);
            } else {
                // Local cache hit with explicit return type cast
                mb.addStatement("return ($T) $L.get($L)", targetBlade, repoBladeBase.toLowerCase() + "Cache", idRef);
            }
        }
        builder.addMethod(mb.build());
    }

    private void ensureCacheField(TypeSpec.Builder b, TypeMirror valType, TypeName idType, String blade, boolean concurrent) {
        String name = blade.toLowerCase() + "Cache";
        if (b.fieldSpecs.stream().anyMatch(f -> f.name.equals(name))) return;
        
        TypeName mapType = ParameterizedTypeName.get(
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentMap") : ClassName.get("java.util", "Map"), 
                idType, TypeName.get(valType));
        
        b.addField(FieldSpec.builder(mapType, name, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", 
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentHashMap") : ClassName.get("java.util", "HashMap")).build());
    }

    private void writeFile(Element origin, String pkg, TypeSpec spec) {
        try {
            JavaFile.builder(pkg, spec)
                    .skipJavaLangImports(true)
                    .build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }

    private void generateBladeRegistry(String targetPkg, Element origin) { copyTemplate(targetPkg, "BladeRegistry", origin); }
    private void generateRepoOps(String targetPkg, Element origin) { copyTemplate(targetPkg, "RepoOps", origin); }

    private void copyTemplate(String targetPkg, String fileName, Element origin) {
        try {
            var is = getClass().getResourceAsStream("/" + fileName + ".txt");
            if (is == null) return;
            String rawContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            
            // Clean package declaration from template and apply targetPkg
            String cleanContent = rawContent.replaceFirst("(?m)^package .*;\\s*", "");
            String finalContent = "package " + targetPkg + ";\n\n" + cleanContent;
            
            var file = processingEnv.getFiler().createSourceFile(targetPkg + "." + fileName, origin);
            try (var writer = file.openWriter()) {
                writer.write(finalContent);
            }
        } catch (IOException ignored) {}
    }
}
