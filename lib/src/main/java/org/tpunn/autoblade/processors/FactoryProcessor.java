package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import dagger.assisted.*;
import org.tpunn.autoblade.annotations.AutoBuilder;
import org.tpunn.autoblade.annotations.AutoFactory;
import org.tpunn.autoblade.utilities.*;

import javax.annotation.processing.*;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.AutoFactory",
    "org.tpunn.autoblade.annotations.AutoBuilder"
})
public class FactoryProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> elements = FileCollector.collectManaged(roundEnv, Set.of(AutoFactory.class, AutoBuilder.class));
        
        for (TypeElement element : elements) {
            // FIX: Use ClassName to resolve the origin package directly
            ClassName originCn = ClassName.get(element);
            String pkg = originCn.packageName();

            String factoryName = FactoryNaming.resolveName(element, processingEnv, AutoFactory.class.getName(), "Factory");
            String builderName = FactoryNaming.resolveName(element, processingEnv, AutoBuilder.class.getName(), "Builder");

            if (factoryName != null) {
                generateFactory(pkg, element, factoryName);
            }
            if (builderName != null && factoryName != null) {
                generateBuilder(pkg, element, builderName, factoryName);
            }
        }
        return true;
    }

    private void generateFactory(String pkg, TypeElement type, String factoryName) {
        ExecutableElement constructor = findAssistedConstructor(type);
        TypeName returnType = TypeName.get(type.asType());

        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(returnType);

        for (VariableElement param : constructor.getParameters()) {
            if (param.getAnnotation(Assisted.class) != null) {
                createMethod.addParameter(ParameterSpec.get(param));
            }
        }

        // Handle Strategy-based Factory Naming
        Optional<? extends AnnotationMirror> strategy = BindingUtils.getStrategyMirror(type);
        if (strategy.isPresent()) {
            // Generate the shared interface (Strategy-agnostic)
            String sharedName = FactoryNaming.resolveName(type, processingEnv, "?", "Factory");
            TypeSpec sharedIface = TypeSpec.interfaceBuilder(sharedName)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(createMethod.build())
                    .build();
            writeSource(pkg, sharedIface);

            // Generate the AssistedFactory implementation
            TypeSpec factoryIface = TypeSpec.interfaceBuilder(factoryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AssistedFactory.class)
                    .addSuperinterface(ClassName.get(pkg, sharedName))
                    .addMethod(createMethod.build())
                    .build();
            writeSource(pkg, factoryIface);
        } else {
            // Standard AssistedFactory
            TypeSpec factoryIface = TypeSpec.interfaceBuilder(factoryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AssistedFactory.class)
                    .addMethod(createMethod.build())
                    .build();
            writeSource(pkg, factoryIface);
        }
    }

    private void generateBuilder(String pkg, TypeElement type, String builderName, String factoryName) {
        ExecutableElement constructor = findAssistedConstructor(type);
        ClassName factoryClass = ClassName.get(pkg, factoryName);
        TypeName returnType = TypeName.get(InterfaceSelector.selectBestInterface(type, processingEnv));
        ClassName builderCn = ClassName.get(pkg, builderName);

        // 1. Builder Interface
        TypeSpec.Builder ifaceBuilder = TypeSpec.interfaceBuilder(builderName)
                .addModifiers(Modifier.PUBLIC);

        // 2. Builder Implementation
        TypeSpec.Builder implBuilder = TypeSpec.classBuilder(builderName + "Impl")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(builderCn);

        implBuilder.addField(factoryClass, "factory", Modifier.PRIVATE, Modifier.FINAL);
        implBuilder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(Inject.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(factoryClass, "factory")
                .addStatement("this.factory = factory")
                .build());

        List<String> assistedParamNames = new ArrayList<>();
        for (VariableElement param : constructor.getParameters()) {
            if (param.getAnnotation(Assisted.class) != null) {
                String pName = param.getSimpleName().toString();
                assistedParamNames.add(pName);
                TypeName pType = TypeName.get(param.asType());

                implBuilder.addField(pType, pName, Modifier.PRIVATE);
                
                // Method for Implementation (with body)
                implBuilder.addMethod(MethodSpec.methodBuilder(pName)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(builderCn)
                        .addParameter(pType, pName)
                        .addStatement("this.$N = $N", pName, pName)
                        .addStatement("return this")
                        .build());

                // Method for Interface (NO body)
                ifaceBuilder.addMethod(MethodSpec.methodBuilder(pName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(builderCn)
                        .addParameter(pType, pName)
                        .build());
            }
        }

        // Build method for Implementation
        implBuilder.addMethod(MethodSpec.methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addStatement("return factory.create($L)", String.join(", ", assistedParamNames))
                .build());

        // Build method for Interface
        ifaceBuilder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(returnType)
                .build());

        writeSource(pkg, ifaceBuilder.build());
        writeSource(pkg, implBuilder.build());
    }

    private ExecutableElement findAssistedConstructor(TypeElement type) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(c -> c.getAnnotation(AssistedInject.class) != null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(type + " missing @AssistedInject"));
    }

    private void writeSource(String pkg, TypeSpec spec) {
        try {
            JavaFile.builder(pkg, spec)
                    .skipJavaLangImports(true)
                    .build()
                    .writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }
}
