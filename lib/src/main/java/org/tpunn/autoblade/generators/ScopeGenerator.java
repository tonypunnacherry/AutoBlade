package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScopeGenerator {
    private final ProcessingEnvironment env;

    public ScopeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    /**
     * Entry point for generating Subcomponents and ManagerServices.
     * Repurposes classes marked @Seed into the backbone of a dynamic scope.
     */
    public void generate(RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Seed.class)) {
            if (!(e instanceof TypeElement)) continue;
            
            TypeElement seedClass = (TypeElement) e;
            String baseName = seedClass.getSimpleName().toString();
            
            // 1. Generate the Subcomponent interface (e.g., UserComponent)
            generateSubcomponent(seedClass, baseName, roundEnv);
            
            // 2. Generate the Lifecycle Manager (e.g., UserService)
            generateManagerService(seedClass, baseName);
        }
    }

    private void generateSubcomponent(TypeElement seed, String name, RoundEnvironment roundEnv) {
        Seed meta = seed.getAnnotation(Seed.class);
        String packageName = "org.tpunn.autoblade";

        TypeSpec.Builder componentBuilder = TypeSpec.interfaceBuilder(name + "Component")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("dagger", "Subcomponent"))
                .addAnnotation(ClassName.get(meta.scope()));

        // Include EntryPoints that match this specific scope
        for (Element e : roundEnv.getElementsAnnotatedWith(EntryPoint.class)) {
            TypeElement entry = (TypeElement) e;
            if (isElementInScope(entry, meta.scope())) {
                componentBuilder.addMethod(EntryPointGenerator.generateGetter(entry));
            }
        }

        // Generate the mandatory @Subcomponent.Builder
        TypeSpec subBuilder = TypeSpec.interfaceBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.INTERFACE)
                .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
                .addMethod(MethodSpec.methodBuilder("seed")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotation(ClassName.get("dagger", "BindsInstance"))
                        .addParameter(TypeName.get(seed.asType()), "data")
                        .returns(ClassName.get(packageName, name + "Component.Builder"))
                        .build())
                .addMethod(MethodSpec.methodBuilder("build")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ClassName.get(packageName, name + "Component"))
                        .build())
                .build();

        componentBuilder.addType(subBuilder);
        writeJavaFile(packageName, componentBuilder.build());
    }

    private void generateManagerService(TypeElement seed, String name) {
        String packageName = "org.tpunn.autoblade";
        TypeName componentType = ClassName.get(packageName, name + "Component");
        TypeName keyType = findIdType(seed);

        TypeSpec.Builder serviceBuilder = TypeSpec.classBuilder(name + "Service")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(javax.inject.Singleton.class)
                .addField(ParameterizedTypeName.get(ClassName.get(ConcurrentHashMap.class), keyType, componentType), 
                    "cache", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addAnnotation(javax.inject.Inject.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("this.cache = new $T<>()", ConcurrentHashMap.class)
                        .build());

        // Pattern: userService.run(key, component -> { ... })
        serviceBuilder.addMethod(MethodSpec.methodBuilder("run")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(keyType, "key")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Consumer.class), componentType), "action")
                .addStatement("$T component = cache.get(key)", componentType)
                .beginControlFlow("if (component != null)")
                .addStatement("action.accept(component)")
                .endControlFlow()
                .build());

        // Pattern: T result = userService.retrieve(key, component -> { return T; })
        TypeVariableName t = TypeVariableName.get("T");
        serviceBuilder.addMethod(MethodSpec.methodBuilder("retrieve")
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(t)
                .returns(t)
                .addParameter(keyType, "key")
                .addParameter(ParameterizedTypeName.get(ClassName.get(Function.class), componentType, t), "action")
                .addStatement("$T component = cache.get(key)", componentType)
                .addStatement("return (component != null) ? action.apply(component) : null")
                .build());

        writeJavaFile(packageName, serviceBuilder.build());
    }

    private TypeName findIdType(TypeElement seed) {
        for (Element enclosed : seed.getEnclosedElements()) {
            if (enclosed.getAnnotation(Id.class) != null) {
                return TypeName.get(enclosed.asType());
            }
        }
        return TypeName.get(Object.class); // Fallback if @Id is missing
    }

    private boolean isElementInScope(TypeElement te, Class<?> scopeClass) {
        for (AnnotationMirror am : te.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(scopeClass.getCanonicalName())) {
                return true;
            }
        }
        return false;
    }

    private void writeJavaFile(String packageName, TypeSpec spec) {
        try {
            JavaFile.builder(packageName, spec).build().writeTo(env.getFiler());
        } catch (IOException e) {
            // Silently fail or log to Messager
        }
    }
}