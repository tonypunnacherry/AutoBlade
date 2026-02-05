package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ComponentProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> allSeeds = FileCollector.collectManaged(roundEnv, Seed.class);
        Set<TypeElement> allContracts = FileCollector.collectManaged(roundEnv, Blade.class);
        if (allContracts.isEmpty()) return true;

        String pkg = GeneratedPackageResolver.computeGeneratedPackage(allContracts, processingEnv);
        
        // Map locations to their respective @Blade interfaces
        Map<String, TypeElement> contractByLoc = allContracts.stream()
                .collect(Collectors.toMap(LocationResolver::resolveLocation, c -> c, (a, b) -> a));

        TypeElement rootContract = contractByLoc.get("App");
        Map<String, List<TypeElement>> seedsByLoc = LocationResolver.groupByAnchor(allSeeds);
        
        // Collect all unique locations to generate subcomponents
        Set<String> allLocs = new HashSet<>(seedsByLoc.keySet());
        allLocs.addAll(contractByLoc.keySet());

        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            generateSubcomponent(pkg, loc, seedsByLoc.getOrDefault(loc, List.of()), contractByLoc.get(loc));
        }

        generateAppBladeAuto(pkg, allLocs, rootContract);
        return true;
    }

    private void generateSubcomponent(String pkg, String loc, List<TypeElement> seeds, TypeElement contract) {
        String autoName = loc + "Blade_Auto";
        ClassName compName = ClassName.get(pkg, autoName);
        ClassName autoModule = ClassName.get(pkg, loc + "AutoModule");

        TypeSpec.Builder comp = TypeSpec.interfaceBuilder(autoName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get(pkg, "Auto" + loc + "Anchor"))
                .addAnnotation(buildDaggerAnnotation("Subcomponent", autoModule, contract))
                .addSuperinterface(getContractType(pkg, loc, contract));

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"));

        // Add the seed() method if this location has managed @Seed classes
        if (!seeds.isEmpty()) {
            builder.addMethod(MethodSpec.methodBuilder("seed")
                    .addAnnotation(ClassName.get("dagger", "BindsInstance"))
                    .addParameter(TypeName.get(seeds.get(0).asType()), "data")
                    .returns(compName.nestedClass("Builder"))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .build());
        }

        comp.addType(builder.addMethod(MethodSpec.methodBuilder("build")
                .returns(compName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .build()).build());

        writeFile(pkg, comp.build());
    }

    private void generateAppBladeAuto(String pkg, Set<String> allLocs, TypeElement root) {
        ClassName autoRootModule = ClassName.get(pkg, "AppAutoModule");
        
        TypeSpec.Builder app = TypeSpec.interfaceBuilder("AppBlade_Auto")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(buildDaggerAnnotation("Component", autoRootModule, root));

        if (root != null) app.addSuperinterface(TypeName.get(root.asType()));

        // Add getters for all Subcomponent Builders to the root component
        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            app.addMethod(MethodSpec.methodBuilder("get" + loc + "Builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(ClassName.get(pkg, loc + "Blade_Auto", "Builder"))
                    .build());
        }

        TypeSpec spec = app.build();
        writeFile(pkg, spec);
        generateAppWrapper(pkg, spec, root);
    }

    private void generateAppWrapper(String pkg, TypeSpec componentSpec, TypeElement rootContract) {
        ClassName daggerComp = ClassName.get(pkg, "Dagger" + componentSpec.name);
        TypeName returnType = rootContract != null ? TypeName.get(rootContract.asType()) : ClassName.get(pkg, componentSpec.name);

        TypeSpec wrapper = TypeSpec.classBuilder("AutoBladeApp")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.methodBuilder("start")
                        .addJavadoc("Bootstraps the AutoBlade graph and returns the root contract.")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(returnType)
                        .addStatement("return $T.create()", daggerComp)
                        .build())
                .build();

        writeFile(pkg, wrapper);
    }

    /** Shared Private Utilities **/

    private AnnotationSpec buildDaggerAnnotation(String daggerType, ClassName autoModule, TypeElement contract) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassName.get("dagger", daggerType))
                .addMember("modules", "$T.class", autoModule);

        if (contract != null) {
            extractModuleTypeNames(contract).forEach(type -> builder.addMember("modules", "$T.class", type));
        }
        return builder.build();
    }

    private List<TypeName> extractModuleTypeNames(TypeElement element) {
        String annotationName = Blade.class.getName();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror == null || mirror.getAnnotationType() == null) continue;
            if (mirror.getAnnotationType().toString().equals(annotationName)) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                    var key = entry.getKey();
                    var val = entry.getValue();
                    if (key == null || val == null) continue;
                    if (key.getSimpleName().contentEquals("legacy")) {
                        @SuppressWarnings("unchecked")
                        List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) val.getValue();
                        return values.stream()
                                .map(v -> {
                                    if (v == null) return null;
                                    return TypeName.get((TypeMirror) v.getValue());
                                })
                                .collect(Collectors.toList());
                    }
                }
            }
        }
        return List.of();
    }

    private TypeName getContractType(String pkg, String loc, TypeElement contract) {
        return contract != null ? TypeName.get(contract.asType()) : ClassName.get(pkg, loc + "Blade");
    }

    private void writeFile(String pkg, TypeSpec spec) {
        try {
            JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "AutoBlade failed to write " + spec.name + ": " + e.getMessage());
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Blade.class.getCanonicalName(), Seed.class.getCanonicalName());
    }
}