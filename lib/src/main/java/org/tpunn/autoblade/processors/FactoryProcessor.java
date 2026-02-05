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
import org.tpunn.autoblade.utilities.FactoryNaming;
import org.tpunn.autoblade.utilities.FileCollector;
import org.tpunn.autoblade.utilities.InterfaceSelector;

import java.util.*;

public class FactoryProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<TypeElement> factories = FileCollector.collectManaged(roundEnv, Set.of(AutoFactory.class, AutoBuilder.class));
        for (Element element : factories) {
            String factoryName = FactoryNaming.resolveName((TypeElement) element, processingEnv, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");
            if (factoryName == null) {
                factoryName = FactoryNaming.resolveName((TypeElement) element, processingEnv, "?", "Factory");
            }
            String builderName = FactoryNaming.resolveName((TypeElement) element, processingEnv, "org.tpunn.autoblade.annotations.AutoBuilder", "Builder");
            generateFactory((TypeElement) element, factoryName);
            System.out.println("Generated Factory: " + factoryName);
            System.out.println("Generated Builder: " + builderName);
            if (builderName != null) {
                generateBuilder((TypeElement) element, builderName, factoryName);
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

        TypeSpec factoryInterface = TypeSpec.interfaceBuilder(factoryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AssistedFactory.class)
                .addMethod(createMethod.build())
                .build();

        writeSource(processingEnv.getElementUtils().getPackageOf(type).toString(), factoryInterface);
    }

    private void generateBuilder(TypeElement type, String builderName, String factoryName) {
        ExecutableElement constructor = findAssistedConstructor(type);
        String pkg = processingEnv.getElementUtils().getPackageOf(type).toString();
        ClassName factoryClass = ClassName.get(pkg, factoryName);
        TypeMirror returnType = InterfaceSelector.selectBestInterface(type, processingEnv);

        TypeSpec.Builder builder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC);

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
                builder.addMethod(MethodSpec.methodBuilder("set" + capitalize(pName))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get(pkg, builderName))
                        .addParameter(TypeName.get(param.asType()), pName)
                        .addStatement("this.$N = $N", pName, pName)
                        .addStatement("return this")
                        .build());
            }
        }

        builder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(returnType))
                .addStatement("return factory.create($L)", String.join(", ", assistedNames))
                .build());

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

    private String capitalize(String s) { return s.substring(0,1).toUpperCase() + s.substring(1); }
}
