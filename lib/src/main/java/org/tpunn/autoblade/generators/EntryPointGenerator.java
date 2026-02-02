package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

public class EntryPointGenerator {
    private final ProcessingEnvironment env;

    public EntryPointGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        Map<String, List<TypeElement>> scopedEntries = new HashMap<>();
        
        // 1. Collect all entry points and sort by scope
        for (Element e : roundEnv.getElementsAnnotatedWith(EntryPoint.class)) {
            TypeElement te = (TypeElement) e;
            String scope = resolveScopeName(te);
            scopedEntries.computeIfAbsent(scope, k -> new ArrayList<>()).add(te);
        }

        // 2. Always ensure "App" bucket exists to force AppComponent generation
        scopedEntries.putIfAbsent("App", new ArrayList<>());

        // 3. Generate getters (This logic will be called by ScopeGenerator/EntryPointGenerator)
        scopedEntries.forEach((scope, elements) -> {
            if (scope.equals("App")) {
                generateAppComponent(elements);
            }
        });
    }

    private void generateAppComponent(List<TypeElement> entries) {
        TypeSpec.Builder builder = TypeSpec.interfaceBuilder("AppComponent")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("dagger", "Component"))
                        .addMember("modules", "AppAutoModule.class")
                        .build())
                .addAnnotation(javax.inject.Singleton.class);

        for (TypeElement te : entries) {
            builder.addMethod(generateGetter(te));
        }

        writeFile(builder.build());
    }

    public static MethodSpec generateGetter(TypeElement te) {
        return MethodSpec.methodBuilder("get" + te.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TypeName.get(te.asType()))
                .build();
    }

    private String resolveScopeName(TypeElement te) {
        for (AnnotationMirror m : te.getAnnotationMirrors()) {
            Element anno = m.getAnnotationType().asElement();
            if (anno.getAnnotation(javax.inject.Scope.class) != null) {
                String name = anno.getSimpleName().toString();
                return name.endsWith("Scope") ? name.substring(0, name.length() - 5) : name;
            }
        }
        return "App";
    }

    private void writeFile(TypeSpec spec) {
        try {
            JavaFile.builder("org.tpunn.autoblade", spec).build().writeTo(env.getFiler());
        } catch (IOException e) {}
    }
}