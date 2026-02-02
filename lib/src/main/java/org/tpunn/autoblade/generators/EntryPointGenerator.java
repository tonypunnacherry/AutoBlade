package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;

public class EntryPointGenerator {
    private final ProcessingEnvironment env;
    public EntryPointGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        // Resolve package from any EntryPoint
        Element first = roundEnv.getElementsAnnotatedWith(EntryPoint.class).stream().findFirst().orElse(null);
        if (first == null) return;
        String pkg = env.getElementUtils().getPackageOf(first).getQualifiedName().toString();

        TypeSpec.Builder app = TypeSpec.interfaceBuilder("AppComponent")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("dagger", "Component"))
                        // Reference the always-generated AppAutoModule
                        .addMember("modules", "$T.class", ClassName.get(pkg, "AppAutoModule"))
                        .build());

        // Provisioning for "App" location (EntryPoints without an anchor)
        roundEnv.getElementsAnnotatedWith(EntryPoint.class).stream()
                .filter(e -> e instanceof TypeElement te)
                .map(e -> (TypeElement) e)
                .filter(te -> {
                    String loc = LocationResolver.resolveLocation(te);
                    return loc == null || loc.isEmpty() || "App".equals(loc);
                })
                .forEach(te -> app.addMethod(ProvisionMethod.generateProvisionMethod(te)));

        // Subcomponent accessors for Seeds
        roundEnv.getElementsAnnotatedWith(Seed.class).stream()
                .filter(s -> s instanceof TypeElement te && !"App".equals(LocationResolver.resolveLocation(te)))
                .forEach(te -> {
                    String loc = LocationResolver.resolveLocation((TypeElement) te);
                    app.addMethod(MethodSpec.methodBuilder("get" + loc + "Builder")
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(ClassName.get(pkg, loc + "Component", "Builder")).build());
                });

        try {
            JavaFile.builder(pkg, app.build()).build().writeTo(env.getFiler());
        } catch (IOException ignored) {}
    }
}
