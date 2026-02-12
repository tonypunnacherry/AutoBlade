package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.Strategy;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.GeneratedPackageResolver;
import org.tpunn.autoblade.utilities.InterfaceSelector;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

public class StrategyProcessor extends AbstractProcessor {

    private final Set<String> processed = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        for (Element element : roundEnv.getElementsAnnotatedWith(Strategy.class)) {
            if (!(element instanceof TypeElement strategyAnno)) continue;

            String annoName = strategyAnno.getSimpleName().toString();
            if (processed.contains(annoName)) continue;

            ExecutableElement valueMethod = strategyAnno.getEnclosedElements().stream()
                    .filter(e -> e != null && e.getSimpleName().contentEquals("value"))
                    .map(e -> (ExecutableElement) e)
                    .findFirst().orElse(null);

            if (valueMethod == null) continue;

            TypeName enumType = TypeName.get(valueMethod.getReturnType());
            String pkg = GeneratedPackageResolver.computeGeneratedPackage(Set.of(strategyAnno), processingEnv);
            
            // 1. Generate @ActionStrategyKey
            generateMapKey(pkg, annoName + "Key", enumType);

            // 2. Generate ActionStrategyResolver
            roundEnv.getElementsAnnotatedWith(strategyAnno).stream()
                    .filter(e -> e instanceof TypeElement)
                    .map(e -> (TypeElement) e)
                    .findFirst()
                    .ifPresent(te -> {
                        TypeMirror iface = InterfaceSelector.selectBestInterface(te, processingEnv);
                        if (iface != null) {
                            generateResolver(te, pkg, annoName + "Resolver", enumType, TypeName.get(iface));
                        }
                    });

            processed.add(annoName);
        }
        return false;
    }

    private void generateResolver(TypeElement te, String pkg, String className, TypeName enumType, TypeName ifaceName) {
        TypeName interfaceType = ifaceName;
        if (BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.AutoBuilder")) {
            // Load builder type
            interfaceType = InterfaceSelector.selectBuilderInterface(pkg, InterfaceSelector.selectBestInterface(te, processingEnv), te, processingEnv);
        } else if (BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.AutoFactory")) {
            // Load factory type
            interfaceType = InterfaceSelector.selectFactoryInterface(pkg, InterfaceSelector.selectBestInterface(te, processingEnv), te, processingEnv);
        }
        TypeName providerType = ParameterizedTypeName.get(ClassName.get("javax.inject", "Provider"), interfaceType);
        TypeName mapType = ParameterizedTypeName.get(ClassName.get("java.util", "Map"), enumType, providerType);

        TypeSpec resolver = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                // FIX: Removed @Singleton. This allows the resolver to live 
                // in the same component as the strategies (e.g. PlayerBlade).
                .addField(mapType, "strategies", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addAnnotation(ClassName.get("javax.inject", "Inject"))
                        .addParameter(mapType, "strategies")
                        .addStatement("this.strategies = strategies")
                        .build())
                .addMethod(MethodSpec.methodBuilder("resolve")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(enumType, "kind")
                        .returns(interfaceType)
                        .addStatement("$T provider = strategies.get(kind)", providerType)
                        .beginControlFlow("if (provider == null)")
                        .addStatement("throw new $T(\"No strategy registered for: \" + kind)", IllegalArgumentException.class)
                        .endControlFlow()
                        .addStatement("return provider.get()")
                        .build())
                .build();

        writeFile(pkg, resolver);
    }

    private void generateMapKey(String pkg, String keyName, TypeName enumType) {
        TypeSpec keySpec = TypeSpec.annotationBuilder(keyName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("dagger", "MapKey"))
                .addAnnotation(AnnotationSpec.builder(java.lang.annotation.Retention.class)
                        .addMember("value", "$T.RUNTIME", java.lang.annotation.RetentionPolicy.class).build())
                .addMethod(MethodSpec.methodBuilder("value").addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).returns(enumType).build())
                .build();
        writeFile(pkg, keySpec);
    }

    private void writeFile(String pkg, TypeSpec typeSpec) {
        try { JavaFile.builder(pkg, typeSpec).build().writeTo(processingEnv.getFiler()); } catch (IOException ignored) {}
    }
}
