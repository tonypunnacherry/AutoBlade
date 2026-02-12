package org.tpunn.autoblade.processors;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Blade;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AnchorProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> anchoredServices = FileCollector.collectManaged(roundEnv, Anchored.class);
        Set<TypeElement> contracts = FileCollector.collectManaged(roundEnv, Blade.class);
        
        if (anchoredServices.isEmpty() && contracts.isEmpty()) return true;

        // Group unique anchor keys
        Set<String> anchorKeys = new HashSet<>();
        for (TypeElement te : anchoredServices) anchorKeys.add(LocationResolver.resolveLocation(te));
        for (TypeElement te : contracts) anchorKeys.add(LocationResolver.resolveLocation(te));

        for (String key : anchorKeys) {
            if ("App".equalsIgnoreCase(key) || key.isEmpty()) continue;

            // Find the @Blade contract that owns this anchor
            TypeElement owner = contracts.stream()
                .filter(c -> key.equalsIgnoreCase(LocationResolver.resolveLocation(c)))
                .findFirst()
                .orElse(null);

            // If no Blade is found, fallback to the first service requesting it
            if (owner == null) {
                owner = anchoredServices.stream()
                    .filter(s -> key.equalsIgnoreCase(LocationResolver.resolveLocation(s)))
                    .findFirst()
                    .orElse(null);
            }

            if (owner != null) {
                generateScope(owner, key);
            }
        }
        return true;
    }

    private void generateScope(TypeElement owner, String rawKey) {
        String pkg = processingEnv.getElementUtils().getPackageOf(owner).getQualifiedName().toString();
        String scopeName = "Auto" + NamingUtils.toPascalCase(rawKey) + "Anchor";

        TypeSpec spec = TypeSpec.annotationBuilder(scopeName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("javax.inject", "Scope"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang.annotation", "Retention"))
                        .addMember("value", "$T.RUNTIME", ClassName.get("java.lang.annotation", "RetentionPolicy"))
                        .build())
                .build();

        try {
            JavaFile.builder(pkg, spec).build().writeTo(processingEnv.getFiler());
        } catch (IOException ignored) {}
    }
}
