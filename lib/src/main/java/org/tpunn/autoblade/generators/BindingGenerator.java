package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

public class BindingGenerator {
    private final ProcessingEnvironment env;

    public BindingGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        Map<String, List<TypeElement>> modules = new HashMap<>();

        // Collect all @Service and @Singleton classes
        Set<Element> managed = new HashSet<>();
        managed.addAll(roundEnv.getElementsAnnotatedWith(Service.class));
        managed.addAll(roundEnv.getElementsAnnotatedWith(javax.inject.Singleton.class));

        for (Element e : managed) {
            TypeElement te = (TypeElement) e;
            String scopeName = resolveScopeName(te);
            modules.computeIfAbsent(scopeName, k -> new ArrayList<>()).add(te);
        }

        modules.forEach(this::writeModule);
    }

    private String resolveScopeName(TypeElement te) {
        for (AnnotationMirror m : te.getAnnotationMirrors()) {
            Element annotation = m.getAnnotationType().asElement();
            if (annotation.getAnnotation(javax.inject.Scope.class) != null) {
                String name = annotation.getSimpleName().toString();
                return name.endsWith("Scope") ? name.substring(0, name.length() - 5) : name;
            }
        }
        return "App";
    }

    private void writeModule(String prefix, List<TypeElement> elements) {
        TypeSpec.Builder builder = TypeSpec.classBuilder(prefix + "AutoModule")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("dagger", "Module"));

        for (TypeElement te : elements) {
            TypeMirror inter = te.getInterfaces().isEmpty() ? te.asType() : te.getInterfaces().get(0);
            MethodSpec.Builder bind = MethodSpec.methodBuilder("bind" + te.getSimpleName())
                    .addAnnotation(ClassName.get("dagger", "Binds"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(TypeName.get(inter))
                    .addParameter(TypeName.get(te.asType()), "impl");

            // Only apply scope if NOT a @Service (Services are transient)
            if (te.getAnnotation(Service.class) == null) {
                applyScope(te, bind);
            }

            builder.addMethod(bind.build());
        }
        
        writeFile(builder.build());
    }

    private void applyScope(TypeElement te, MethodSpec.Builder bind) {
        for (AnnotationMirror m : te.getAnnotationMirrors()) {
            if (m.getAnnotationType().asElement().getAnnotation(javax.inject.Scope.class) != null) {
                bind.addAnnotation(AnnotationSpec.get(m));
            }
        }
    }

    private void writeFile(TypeSpec spec) {
        try {
            JavaFile.builder("org.tpunn.autoblade", spec).build().writeTo(env.getFiler());
        } catch (IOException e) { /* Log error */ }
    }
}