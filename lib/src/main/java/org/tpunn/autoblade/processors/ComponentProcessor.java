package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

public class ComponentProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> allSeeds = FileCollector.collectManaged(roundEnv, Seed.class);
        Set<TypeElement> allContracts = FileCollector.collectManaged(roundEnv, Blade.class);

        if (allContracts.isEmpty()) return true;

        // Resolve common package based on all Blade contracts
        String pkg = GeneratedPackageResolver.computeGeneratedPackage(allContracts, processingEnv);

        Map<String, TypeElement> contractByLoc = new HashMap<>();
        TypeElement rootContract = null;
        for (TypeElement contract : allContracts) {
            String loc = LocationResolver.resolveLocation(contract);
            if ("App".equalsIgnoreCase(loc)) {
                rootContract = contract;
                contractByLoc.put("App", contract);
            } else contractByLoc.put(loc, contract);
        }

        Map<String, List<TypeElement>> seedsByLoc = LocationResolver.groupByAnchor(allSeeds);
        Set<String> allLocs = new HashSet<>(seedsByLoc.keySet());
        allLocs.addAll(contractByLoc.keySet());

        // Generate Subcomponents
        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            generateSubcomponent(pkg, loc, seedsByLoc.getOrDefault(loc, Collections.emptyList()), contractByLoc.get(loc));
        }

        // Generate Root AppBlade_Auto
        generateAppBladeAuto(pkg, allLocs, rootContract);
        return true;
    }

    private void generateSubcomponent(String pkg, String loc, List<TypeElement> seeds, TypeElement contract) {
        String autoName = loc + "Blade_Auto";
        ClassName compName = ClassName.get(pkg, autoName);
        ClassName autoModule = ClassName.get(pkg, loc + "AutoModule");
        TypeName stableInterface = (contract != null) ? TypeName.get(contract.asType()) : ClassName.get(pkg, loc + "Blade");

        String scopeName = "Auto" + loc + "Anchor"; 
        ClassName scopeAnno = ClassName.get(pkg, scopeName);

        TypeSpec.Builder comp = TypeSpec.interfaceBuilder(autoName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(scopeAnno)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"))
                        .addMember("modules", "$T.class", autoModule).build())
                .addSuperinterface(stableInterface);

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"));

        if (!seeds.isEmpty()) {
            builder.addMethod(MethodSpec.methodBuilder("seed")
                    .addAnnotation(ClassName.get("dagger", "BindsInstance"))
                    .addParameter(TypeName.get(seeds.get(0).asType()), "data")
                    .returns(compName.nestedClass("Builder")).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build());
        }

        comp.addType(builder.addMethod(MethodSpec.methodBuilder("build").returns(compName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build()).build());

        writeFile(pkg, comp.build());
    }

    private void generateAppBladeAuto(String pkg, Set<String> allLocs, TypeElement root) {
        ClassName autoRootModule = ClassName.get(pkg, "AppAutoModule");
        TypeSpec.Builder app = TypeSpec.interfaceBuilder("AppBlade_Auto").addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("dagger", "Component"))
                        .addMember("modules", "$T.class", autoRootModule).build());

        if (root != null) app.addSuperinterface(TypeName.get(root.asType()));

        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            app.addMethod(MethodSpec.methodBuilder("get" + loc + "Builder").addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(ClassName.get(pkg, loc + "Blade_Auto", "Builder")).build());
        }
        writeFile(pkg, app.build());
    }

    private void writeFile(String pkg, TypeSpec spec) {
        try { JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler()); } catch (IOException ignored) {}
    }
}
