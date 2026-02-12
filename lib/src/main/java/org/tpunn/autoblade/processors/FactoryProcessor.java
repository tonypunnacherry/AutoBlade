package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import dagger.assisted.*;
import javax.annotation.processing.*;
import javax.inject.Inject;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import org.tpunn.autoblade.annotations.AutoBuilder;
import org.tpunn.autoblade.annotations.AutoFactory;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.FactoryNaming;
import org.tpunn.autoblade.utilities.FileCollector;
import org.tpunn.autoblade.utilities.InterfaceSelector;

import java.util.*;

public class FactoryProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<TypeElement> factories = FileCollector.collectManaged(roundEnv, Set.of(AutoFactory.class, AutoBuilder.class));
        for (TypeElement element : factories) {
            String factoryName = FactoryNaming.resolveName(element, processingEnv, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");
            if (factoryName == null) {
                factoryName = FactoryNaming.resolveName(element, processingEnv, "?", "Factory");
            }
            String builderName = FactoryNaming.resolveName(element, processingEnv, "org.tpunn.autoblade.annotations.AutoBuilder", "Builder");
            generateFactory(element, factoryName);
            System.out.println("Generated Factory: " + factoryName);
            if (builderName != null) {
                generateBuilder(element, builderName, factoryName);
                System.out.println("Generated Builder: " + builderName);
            }
        }
        return true;
    }

    private void generateFactory(TypeElement type, String factoryName) {
        ExecutableElement constructor = findAssistedConstructor(type);
        TypeMirror returnType = type.asType();

        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TypeName.get(returnType));

        for (VariableElement param : constructor.getParameters()) {
            if (param == null) continue;
            if (param.getAnnotation(Assisted.class) != null) {
                createMethod.addParameter(ParameterSpec.get(param));
            }
        }

        Optional<? extends AnnotationMirror> strategy = BindingUtils.getStrategyMirror(type);
        TypeSpec factorySharedInterface;
        if (strategy.isPresent()) {
            // Force fallback
            factoryName = FactoryNaming.resolveName(type, processingEnv, "?", "Factory");
            factorySharedInterface = TypeSpec.interfaceBuilder(factoryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(createMethod.build())
                    .build();

            String localFactoryName = FactoryNaming.resolveName(returnType, type, processingEnv, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");

            TypeSpec factoryInterface = TypeSpec.interfaceBuilder(localFactoryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(ClassName.get(processingEnv.getElementUtils().getPackageOf(type).toString(), factoryName))
                    .addAnnotation(AssistedFactory.class)
                    .addMethod(createMethod.build())
                    .build();

            writeSource(processingEnv.getElementUtils().getPackageOf(type).toString(), factoryInterface);
        } else {
            factorySharedInterface = TypeSpec.interfaceBuilder(factoryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AssistedFactory.class)
                    .addMethod(createMethod.build())
                    .build();
        }

        writeSource(processingEnv.getElementUtils().getPackageOf(type).toString(), factorySharedInterface);
    }

    private void generateBuilder(TypeElement type, String builderName, String factoryName) {
        ExecutableElement constructor = findAssistedConstructor(type);
        String pkg = processingEnv.getElementUtils().getPackageOf(type).toString();
        Optional<? extends AnnotationMirror> strategy = BindingUtils.getStrategyMirror(type);
        if (strategy.isPresent()) {
            factoryName = FactoryNaming.resolveName(type.asType(), type, processingEnv, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");
        }
        ClassName factoryClass = ClassName.get(pkg, factoryName);
        TypeMirror returnType = InterfaceSelector.selectBestInterface(type, processingEnv);

        TypeSpec.Builder builderIface = TypeSpec.interfaceBuilder(builderName)
                .addModifiers(Modifier.PUBLIC);

        String localBuilderName = FactoryNaming.resolveName(type.asType(), type, processingEnv, "org.tpunn.autoblade.annotations.AutoBuilder", "Builder");

        TypeSpec.Builder builder = TypeSpec.classBuilder(localBuilderName)
                .addModifiers(Modifier.PUBLIC);

        builder.addSuperinterface(ClassName.get(pkg, builderName));

        // Inject Factory
        builder.addField(factoryClass, "factory", Modifier.PRIVATE, Modifier.FINAL);
        builder.addMethod(MethodSpec.constructorBuilder()
                .addAnnotation(Inject.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(factoryClass, "factory")
                .addStatement("this.factory = factory")
                .build());

        List<String> assistedNames = new ArrayList<>();
        for (VariableElement param : constructor.getParameters()) {
            if (param == null) continue;
            if (param.getAnnotation(Assisted.class) != null) {
                String pName = param.getSimpleName().toString();
                assistedNames.add(pName);
                builder.addField(TypeName.get(param.asType()), pName, Modifier.PRIVATE);
                builder.addMethod(MethodSpec.methodBuilder(pName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get(pkg, builderName))
                        .addParameter(TypeName.get(param.asType()), pName)
                        .addStatement("this.$N = $N", pName, pName)
                        .addStatement("return this")
                        .build());
                builderIface.addMethod(MethodSpec.methodBuilder(pName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(ClassName.get(pkg, builderName))
                        .addParameter(TypeName.get(param.asType()), pName)
                        .build());
            }
        }

        builder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(returnType))
                .addStatement("return factory.create($L)", String.join(", ", assistedNames))
                .build());
        builderIface.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TypeName.get(returnType))
                .build());

        writeSource(pkg, builderIface.build());
        writeSource(pkg, builder.build());
    }

    private ExecutableElement findAssistedConstructor(TypeElement type) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(c -> c.getAnnotation(AssistedInject.class) != null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(type + " missing @AssistedInject"));
    }

    private void writeSource(String pkg, TypeSpec spec) {
        try {
            JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler());
        } catch (Exception ignored) {}
    }

    //private String capitalize(String s) { return s.substring(0,1).toUpperCase() + s.substring(1); }
}
