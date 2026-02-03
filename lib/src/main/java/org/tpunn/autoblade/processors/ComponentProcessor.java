package org.tpunn.autoblade.processors;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.EntryPoint;
import org.tpunn.autoblade.annotations.Seed;
import org.tpunn.autoblade.utilities.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.*;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.EntryPoint",
    "org.tpunn.autoblade.annotations.Seed"
})
public class ComponentProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        // 1. Collect all elements
        Set<TypeElement> allSeeds = FileCollector.collectManaged(roundEnv, Seed.class);
        Set<TypeElement> allEntryPoints = FileCollector.collectManaged(roundEnv, EntryPoint.class);

        if (allSeeds.isEmpty() && allEntryPoints.isEmpty()) return true;

        // 2. Group everything by Anchor Location
        Map<String, List<TypeElement>> seedsByLoc = new LinkedHashMap<>();
        allSeeds.forEach(s -> seedsByLoc.computeIfAbsent(LocationResolver.resolveLocation(s), k -> new ArrayList<>()).add(s));

        Map<String, List<TypeElement>> epsByLoc = new LinkedHashMap<>();
        allEntryPoints.forEach(ep -> {
            String loc = LocationResolver.resolveLocation(ep);
            epsByLoc.computeIfAbsent((loc == null || loc.isEmpty()) ? "App" : loc, k -> new ArrayList<>()).add(ep);
        });

        // 3. Resolve base package (using first available EntryPoint or Seed)
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(
            !allEntryPoints.isEmpty() ? Set.of(allEntryPoints.iterator().next()) : allSeeds, processingEnv);

        // 4. Generate Subcomponents for each Seed Location
        for (String loc : seedsByLoc.keySet()) {
            if ("App".equals(loc)) continue;
            generateSubcomponent(pkg, loc, seedsByLoc.get(loc), epsByLoc.getOrDefault(loc, List.of()));
        }

        // 5. Generate Root AppComponent
        generateAppComponent(pkg, seedsByLoc, epsByLoc.getOrDefault("App", List.of()));

        return true;
    }

    private void generateSubcomponent(String pkg, String loc, List<TypeElement> seeds, List<TypeElement> entryPoints) {
        ClassName compName = ClassName.get(pkg, loc + "Blade");
        TypeSpec.Builder comp = TypeSpec.interfaceBuilder(compName.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get(pkg, "Auto" + loc + "Anchor"));

        // Only include module if there are entry points to provide
        AnnotationSpec.Builder subBuilder = AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"));
        if (!entryPoints.isEmpty()) {
            subBuilder.addMember("modules", "$T.class", ClassName.get(pkg, loc + "AutoModule"));
        }
        comp.addAnnotation(subBuilder.build());

        seeds.forEach(s -> comp.addMethod(ProvisionMethod.generateProvisionMethod(s)));
        entryPoints.forEach(ep -> comp.addMethod(ProvisionMethod.generateProvisionMethod(ep)));

        // Builder logic...
        comp.addType(TypeSpec.interfaceBuilder("Builder").addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
            .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
            .addMethod(MethodSpec.methodBuilder("seed").addAnnotation(ClassName.get("dagger", "BindsInstance"))
                .addParameter(TypeName.get(seeds.get(0).asType()), "data").returns(compName.nestedClass("Builder"))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build())
            .addMethod(MethodSpec.methodBuilder("build").returns(compName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build()).build());

        writeFile(pkg, comp.build());
    }

    private void generateAppComponent(String pkg, Map<String, List<TypeElement>> seedsByLoc, List<TypeElement> appEntryPoints) {
        TypeSpec.Builder app = TypeSpec.interfaceBuilder("AppBlade")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get("javax.inject", "Singleton"));

        // Only include AppAutoModule if App-level entry points exist
        AnnotationSpec.Builder compBuilder = AnnotationSpec.builder(ClassName.get("dagger", "Component"));
        if (!appEntryPoints.isEmpty()) {
            compBuilder.addMember("modules", "$T.class", ClassName.get(pkg, "AppAutoModule"));
        }
        app.addAnnotation(compBuilder.build());

        appEntryPoints.forEach(ep -> app.addMethod(ProvisionMethod.generateProvisionMethod(ep)));

        seedsByLoc.keySet().stream().filter(loc -> !"App".equals(loc)).forEach(loc -> {
            app.addMethod(MethodSpec.methodBuilder("get" + loc + "Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get(pkg, loc + "Blade", "Builder")).build());
        });

        writeFile(pkg, app.build());
    }

    private void writeFile(String pkg, TypeSpec typeSpec) {
        try {
            JavaFile.builder(pkg, typeSpec).build().writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }
}
