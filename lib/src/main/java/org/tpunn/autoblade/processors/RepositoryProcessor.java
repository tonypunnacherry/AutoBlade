package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Provider;

public class RepositoryProcessor extends AbstractProcessor {

    private boolean registryGenerated = false;
    private Map<String, TypeElement> anchorToSeedMap = new HashMap<>();

    /** Injected by AutoBladeProcessor to ensure cross-round persistence */
    public void setAnchorMap(Map<String, TypeElement> map) {
        this.anchorToSeedMap = map;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> repos = FileCollector.collectManaged(roundEnv, Repository.class);
        if (repos.isEmpty()) return true;

        for (TypeElement repo : repos) {
            String pkg = GeneratedPackageResolver.computeGeneratedPackage(Collections.singleton(repo), processingEnv);
            
            if (!registryGenerated) {
                generateBladeRegistry(pkg);
                generateRepoOps(pkg);
                registryGenerated = true;
            }

            generateRepoImpl(repo, pkg);
        }
        return true;
    }

    private void generateRepoImpl(TypeElement repo, String pkg) {
        if (repo.getKind() == ElementKind.CLASS && repo.getInterfaces().isEmpty()) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Repository class " + repo.getSimpleName() + " must implement an interface to be used as a contract.",
                repo
            );
            return;
        }

        String implName = repo.getSimpleName() + "_Repo";
        boolean isConcurrent = BindingUtils.hasAnnotation(repo, "org.tpunn.autoblade.annotations.Concurrent");
        
        ClassName registryType = ClassName.get(pkg, "BladeRegistry");
        ClassName repoOps = ClassName.get(pkg, "RepoOps");
        String bladeBase = BindingUtils.parseBladeNameFromRepo(repo);
        
        ClassName autoBladeType = ClassName.get(pkg, bladeBase + "Blade_Auto");
        TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), autoBladeType.nestedClass("Builder"));

        TypeSpec.Builder builder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "all").build());

        if (repo.getKind() == ElementKind.INTERFACE) {
            builder.addSuperinterface(TypeName.get(repo.asType()));
        } else {
            builder.superclass(TypeName.get(repo.asType()));
        }

        builder.addField(registryType, "registry", Modifier.PRIVATE, Modifier.FINAL)
               .addField(providerType, "builderProvider", Modifier.PRIVATE, Modifier.FINAL);

        builder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(ClassName.get("javax.inject", "Inject"))
                .addParameter(registryType, "registry")
                .addParameter(providerType, "builderProvider")
                .addStatement("this.registry = registry")
                .addStatement("this.builderProvider = builderProvider")
                .build());

        for (Element e : repo.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement m = (ExecutableElement) e;
            
            if (m.getModifiers().contains(Modifier.ABSTRACT)) {
                processMethod(builder, m, bladeBase, isConcurrent, repoOps);
            }
        }

        writeFile(pkg, builder.build());
    }

    private void processMethod(TypeSpec.Builder builder, ExecutableElement m, String repoBladeBase, boolean isConcurrent, ClassName repoOps) {
        if (m.getParameters().isEmpty()) return;
        
        VariableElement param = m.getParameters().get(0);
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

    private void ensureCacheField(TypeSpec.Builder b, javax.lang.model.type.TypeMirror valType, TypeName idType, String blade, boolean concurrent) {
        String name = blade.toLowerCase() + "Cache";
        if (b.fieldSpecs.stream().anyMatch(f -> f.name.equals(name))) return;
        
        TypeName mapType = ParameterizedTypeName.get(
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentMap") : ClassName.get("java.util", "Map"), 
                idType, TypeName.get(valType));
        
        b.addField(FieldSpec.builder(mapType, name, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", 
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentHashMap") : ClassName.get("java.util", "HashMap")).build());
    }

    private void writeFile(String pkg, TypeSpec spec) {
        try { JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler()); } 
        catch (IOException e) { processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Write Failed: " + e.getMessage()); }
    }

    private void generateBladeRegistry(String targetPkg) { copyTemplate(targetPkg, "BladeRegistry"); }
    private void generateRepoOps(String targetPkg) { copyTemplate(targetPkg, "RepoOps"); }

    private void copyTemplate(String targetPkg, String fileName) {
        try {
            var resourcePath = "/" + fileName + ".txt";
            var is = getClass().getResourceAsStream(resourcePath);
            if (is == null) return;
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            content = content.replaceFirst("(?m)^package .*;\\s*", "package " + targetPkg + ";\n\n");
            JavaFileObject file = processingEnv.getFiler().createSourceFile(targetPkg + "." + fileName);
            try (var writer = file.openWriter()) { writer.write(content); }
        } catch (IOException ignored) {}
    }
}
