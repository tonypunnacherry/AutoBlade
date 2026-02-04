package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Provider;

public class RepositoryProcessor extends AbstractProcessor {

    private boolean registryGenerated = false;

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
        String implName = repo.getSimpleName() + "_Repo";
        boolean isConcurrent = BindingUtils.hasAnnotation(repo, "org.tpunn.autoblade.annotations.Concurrent");
        
        ClassName registryType = ClassName.get(pkg, "BladeRegistry");
        ClassName repoOps = ClassName.get(pkg, "RepoOps");
        
        String bladeBase = BindingUtils.parseBladeNameFromRepo(repo);
        
        // Target the generated _Auto builder
        ClassName autoBladeType = ClassName.get(pkg, bladeBase + "Blade_Auto");
        ClassName autoBuilderType = autoBladeType.nestedClass("Builder");
        TypeName providerType = ParameterizedTypeName.get(ClassName.get(Provider.class), autoBuilderType);

        TypeSpec.Builder builder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(TypeName.get(repo.asType()))
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "all").build());

        // Registry and BuilderProvider fields
        builder.addField(registryType, "registry", Modifier.PRIVATE, Modifier.FINAL)
               .addField(providerType, "builderProvider", Modifier.PRIVATE, Modifier.FINAL);

        // Constructor
        builder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(ClassName.get("javax.inject", "Inject"))
                .addParameter(registryType, "registry")
                .addParameter(providerType, "builderProvider")
                .addStatement("this.registry = registry")
                .addStatement("this.builderProvider = builderProvider")
                .build());

        // Process @Create and @Lookup methods
        for (Element e : repo.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) continue;
            ExecutableElement m = (ExecutableElement) e;
            processMethod(builder, m, bladeBase, isConcurrent, repoOps);
        }

        writeFile(pkg, builder.build());
    }

    private void processMethod(TypeSpec.Builder builder, ExecutableElement m, String bladeBase, boolean isConcurrent, ClassName repoOps) {
        String cacheName = bladeBase.toLowerCase() + "Cache";
        if (m.getParameters().isEmpty()) return;
        String paramName = m.getParameters().get(0).getSimpleName().toString();
        MethodSpec.Builder mb = MethodSpec.overriding(m);

        if (BindingUtils.hasAnnotation(m, "org.tpunn.autoblade.annotations.Create")) {
            TypeElement record = (TypeElement) processingEnv.getTypeUtils().asElement(m.getParameters().get(0).asType());
            String idAcc = BindingUtils.resolveIdAccessor(record);
            ensureCacheField(builder, m.getReturnType(), bladeBase, isConcurrent);

            // Use the _Auto builder to create the blade
            CodeBlock lambdaBody = CodeBlock.builder()
                .add("var blade = builderProvider.get().seed($L).build();\n", paramName)
                .add("registry.register($L.$L(), blade, null);\n", paramName, idAcc)
                .add("return blade;")
                .build();

            mb.addStatement("return $T.createAtomic($L, $L.$L(), () -> {\n$L\n})", 
                    repoOps, cacheName, paramName, idAcc, lambdaBody);            

        } else if (BindingUtils.hasAnnotation(m, "org.tpunn.autoblade.annotations.Lookup")) {
            TypeName returnType = TypeName.get(m.getReturnType());
            String typeStr = returnType.toString();
            
            // Check return type for Optional/Collections vs Direct Cache hit
            if (typeStr.contains("java.util.Optional")) {
                mb.addStatement("return registry.find($L)", paramName);
            } else if (typeStr.contains("java.util.Set") || typeStr.contains("java.util.List")) {
                mb.addStatement("return registry.findAll($L, $T.class)", paramName, getBladeContractType(m));
            } else {
                mb.addStatement("return $L.get($L)", cacheName, paramName);
            }
        }
        builder.addMethod(mb.build());
    }

    private void ensureCacheField(TypeSpec.Builder b, javax.lang.model.type.TypeMirror type, String blade, boolean concurrent) {
        String name = blade.toLowerCase() + "Cache";
        if (b.fieldSpecs.stream().anyMatch(f -> f.name.equals(name))) return;
        
        TypeName mapType = ParameterizedTypeName.get(
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentMap") : ClassName.get("java.util", "Map"), 
                ClassName.get(String.class), TypeName.get(type));
        
        b.addField(FieldSpec.builder(mapType, name, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", 
                concurrent ? ClassName.get("java.util.concurrent", "ConcurrentHashMap") : ClassName.get("java.util", "HashMap")).build());
    }

    private TypeName getBladeContractType(ExecutableElement m) {
        var returnType = (javax.lang.model.type.DeclaredType) m.getReturnType();
        return TypeName.get(returnType.getTypeArguments().get(0));
    }

    private void writeFile(String pkg, TypeSpec spec) {
        try { JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler()); } catch (IOException ignored) {}
    }

    private void generateBladeRegistry(String targetPkg) { copyTemplate(targetPkg, "BladeRegistry"); }
    private void generateRepoOps(String targetPkg) { copyTemplate(targetPkg, "RepoOps"); }

    private void copyTemplate(String targetPkg, String fileName) {
        try {
            var resourcePath = "/" + fileName + ".txt";
            var is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "AutoBlade Template Not Found: " + resourcePath);
                return;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            content = content.replaceFirst("(?m)^package .*;\\s*", "package " + targetPkg + ";\n\n");
            JavaFileObject file = processingEnv.getFiler().createSourceFile(targetPkg + "." + fileName);
            try (var writer = file.openWriter()) { writer.write(content); }
        } catch (IOException ignored) {}
    }
}
