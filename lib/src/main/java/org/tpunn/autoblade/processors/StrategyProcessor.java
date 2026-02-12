package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.Strategy;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.InterfaceSelector;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("org.tpunn.autoblade.annotations.Strategy")
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
                    .filter(e -> e.getKind() == ElementKind.METHOD && e.getSimpleName().contentEquals("value"))
                    .map(e -> (ExecutableElement) e)
                    .findFirst().orElse(null);

            if (valueMethod == null) continue;

            // Use JavaPoet's ClassName to handle the package automatically
            ClassName strategyCn = ClassName.get(strategyAnno);
            TypeName enumType = TypeName.get(valueMethod.getReturnType());
            
            // 1. Generate @[Name]Key as a peer to the annotation
            generateMapKey(strategyCn, annoName + "Key", enumType);

            // 2. Generate [Name]Resolver as a peer to the annotation
            roundEnv.getElementsAnnotatedWith(strategyAnno).stream()
                    .filter(e -> e instanceof TypeElement)
                    .map(e -> (TypeElement) e)
                    .findFirst()
                    .ifPresent(te -> {
                        TypeMirror ifaceMirror = InterfaceSelector.selectBestInterface(te, processingEnv);
                        if (ifaceMirror != null) {
                            generateResolver(te, strategyCn, annoName + "Resolver", enumType, ifaceMirror);
                        }
                    });

            processed.add(annoName);
        }
        return true;
    }

    private void generateResolver(TypeElement te, ClassName strategyCn, String className, TypeName enumType, TypeMirror ifaceMirror) {
        String pkg = strategyCn.packageName();
        TypeName interfaceType = TypeName.get(ifaceMirror);
        
        // Resolve Builder/Factory interfaces relative to the service element
        if (BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.AutoBuilder")) {
            interfaceType = InterfaceSelector.selectBuilderInterface(pkg, ifaceMirror, te, processingEnv);
        } else if (BindingUtils.hasMirror(te, "org.tpunn.autoblade.annotations.AutoFactory")) {
            interfaceType = InterfaceSelector.selectFactoryInterface(pkg, ifaceMirror, te, processingEnv);
        }

        TypeName providerType = ParameterizedTypeName.get(ClassName.get("javax.inject", "Provider"), interfaceType);
        TypeName mapType = ParameterizedTypeName.get(ClassName.get("java.util", "Map"), enumType, providerType);

        TypeSpec resolver = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
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

    private void generateMapKey(ClassName strategyCn, String keyName, TypeName enumType) {
        TypeSpec keySpec = TypeSpec.annotationBuilder(keyName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("dagger", "MapKey"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang.annotation", "Retention"))
                        .addMember("value", "$T.RUNTIME", ClassName.get("java.lang.annotation", "RetentionPolicy"))
                        .build())
                .addMethod(MethodSpec.methodBuilder("value")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(enumType)
                        .build())
                .build();

        writeFile(strategyCn.packageName(), keySpec);
    }

    private void writeFile(String pkg, TypeSpec typeSpec) {
        try {
            JavaFile.builder(pkg, typeSpec)
                    .skipJavaLangImports(true)
                    .build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }
}
