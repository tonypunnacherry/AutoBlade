package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ComponentProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> allSeeds = FileCollector.collectManaged(roundEnv, Seed.class);
        Set<TypeElement> allContracts = FileCollector.collectManaged(roundEnv, Blade.class);
        if (allContracts.isEmpty()) return true;

        // Group contracts by their anchor location
        Map<String, TypeElement> contractByLoc = allContracts.stream()
                .collect(Collectors.toMap(LocationResolver::resolveLocation, c -> c, (a, b) -> a));

        Map<String, List<TypeElement>> seedsByLoc = LocationResolver.groupByAnchor(allSeeds);
        
        Set<String> allLocs = new HashSet<>(seedsByLoc.keySet());
        allLocs.addAll(contractByLoc.keySet());

        // Use the App Blade's package for the root component, fallback to common root
        TypeElement rootContract = contractByLoc.get("App");
        String rootPkg = rootContract != null 
            ? GeneratedPackageResolver.getPackage(rootContract, processingEnv)
            : GeneratedPackageResolver.computeGeneratedPackage(allContracts, processingEnv);

        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            generateSubcomponent(rootPkg, loc, seedsByLoc.getOrDefault(loc, List.of()), contractByLoc.get(loc));
        }

        generateAppBladeAuto(rootPkg, allLocs, rootContract, contractByLoc);
        return true;
    }

    private void generateSubcomponent(String rootPkg, String loc, List<TypeElement> seeds, TypeElement contract) {
        // Find the specific package for this Blade contract
        String pkg = contract != null ? GeneratedPackageResolver.getPackage(contract, processingEnv) : rootPkg;
        ClassName compName = ClassName.get(pkg, loc + "Blade_Auto");
        ClassName autoModule = ClassName.get(rootPkg, loc + "AutoModule");
        ClassName anchorScope = ClassName.get(pkg, "Auto" + NamingUtils.toPascalCase(loc) + "Anchor");

        TypeSpec.Builder comp = TypeSpec.interfaceBuilder(compName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(anchorScope)
                .addAnnotation(buildDaggerAnnotation("Subcomponent", autoModule, contract))
                .addSuperinterface(contract != null ? TypeName.get(contract.asType()) : ClassName.get(pkg, loc + "Blade"));

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"));

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

    private void generateAppBladeAuto(String pkg, Set<String> allLocs, TypeElement root, Map<String, TypeElement> contractByLoc) {
        ClassName autoRootModule = ClassName.get(pkg, "AppAutoModule");
        ClassName appBladeAuto = ClassName.get(pkg, "AppBlade_Auto");
        
        TypeSpec.Builder app = TypeSpec.interfaceBuilder(appBladeAuto)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("javax.inject", "Singleton"))
                .addAnnotation(buildDaggerAnnotation("Component", autoRootModule, root));

        if (root != null) app.addSuperinterface(TypeName.get(root.asType()));

        for (String loc : allLocs) {
            if ("App".equalsIgnoreCase(loc)) continue;
            
            TypeElement subContract = contractByLoc.get(loc);
            String subPkg = subContract != null ? GeneratedPackageResolver.getPackage(subContract, processingEnv) : pkg;
            
            app.addMethod(MethodSpec.methodBuilder("get" + NamingUtils.toPascalCase(loc) + "Builder")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(ClassName.get(subPkg, loc + "Blade_Auto", "Builder"))
                    .build());
        }

        TypeSpec spec = app.build();
        writeFile(pkg, spec);
        generateAppWrapper(pkg, appBladeAuto, root);
    }

    private void generateAppWrapper(String pkg, ClassName componentCn, TypeElement rootContract) {
        ClassName daggerComp = ClassName.get(pkg, "Dagger" + componentCn.simpleName());
        TypeName returnType = rootContract != null ? TypeName.get(rootContract.asType()) : componentCn;

        TypeSpec wrapper = TypeSpec.classBuilder("AutoBladeApp")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.methodBuilder("start")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(returnType)
                        .addStatement("return $T.create()", daggerComp)
                        .build())
                .build();

        writeFile(pkg, wrapper);
    }

    private AnnotationSpec buildDaggerAnnotation(String daggerType, ClassName autoModule, TypeElement contract) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassName.get("dagger", daggerType))
                .addMember("modules", "$T.class", autoModule);

        if (contract != null) {
            extractLegacyModules(contract).forEach(type -> builder.addMember("modules", "$T.class", type));
        }
        return builder.build();
    }

    private List<TypeName> extractLegacyModules(TypeElement element) {
        return element.getAnnotationMirrors().stream()
            .filter(m -> m.getAnnotationType().toString().equals(Blade.class.getName()))
            .flatMap(m -> m.getElementValues().entrySet().stream())
            .filter(e -> e.getKey().getSimpleName().contentEquals("legacy"))
            .flatMap(e -> ((List<?>) e.getValue().getValue()).stream())
            .map(v -> TypeName.get((TypeMirror) ((AnnotationValue) v).getValue()))
            .collect(Collectors.toList());
    }

    private void writeFile(String pkg, TypeSpec spec) {
        try {
            JavaFile.builder(pkg, spec).skipJavaLangImports(true).build().writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Blade.class.getCanonicalName(), Seed.class.getCanonicalName());
    }
}
