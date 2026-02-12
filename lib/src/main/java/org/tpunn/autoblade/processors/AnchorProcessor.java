package org.tpunn.autoblade.processors;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.utilities.FileCollector;
import org.tpunn.autoblade.utilities.GeneratedPackageResolver;
import org.tpunn.autoblade.utilities.LocationResolver;
import org.tpunn.autoblade.utilities.NamingUtils;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AnchorProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        Set<TypeElement> anchoredServices = FileCollector.collectManaged(roundEnv, Anchored.class);
        if (anchoredServices.isEmpty()) return true;

        Map<String, Set<TypeElement>> byPrefix = new LinkedHashMap<>();

        for (TypeElement service : anchoredServices) {
            String loc = LocationResolver.resolveLocation(service);
            if (loc == null || loc.isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                    "AutoBlade: @Anchored must specify a non-empty string key.", service);
                continue;
            }
            if ("App".equals(loc)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                    "AutoBlade: @Anchored with 'App' value is reserved for the root scope.", service);
                continue;
            }
            String prefix = NamingUtils.toPascalCase(loc);
            byPrefix.computeIfAbsent(prefix, k -> new LinkedHashSet<>()).add(service);
        }

        String pkg = GeneratedPackageResolver.computeGeneratedPackage(anchoredServices, processingEnv);

        for (Map.Entry<String, Set<TypeElement>> entry : byPrefix.entrySet()) {
            String prefix = entry.getKey();
            Set<TypeElement> origins = entry.getValue();
            String generatedName = "Auto" + prefix + "Anchor";
            
            TypeSpec spec = TypeSpec.annotationBuilder(generatedName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(ClassName.get("javax.inject", "Scope"))
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang.annotation", "Retention"))
                            .addMember("value", "$T.RUNTIME", ClassName.get("java.lang.annotation", "RetentionPolicy"))
                            .build())
                    .build();

            try {
                TypeElement[] originArray = origins.toArray(new TypeElement[0]);
                JavaFile javaFile = JavaFile.builder(pkg, spec).build();
                
                try (Writer writer = processingEnv.getFiler()
                        .createSourceFile(pkg + "." + generatedName, originArray)
                        .openWriter()) {
                    writer.write(javaFile.toString());
                }
                
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, 
                    "AutoBlade: Generated anchor scope: " + pkg + "." + generatedName);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, 
                    "AutoBlade: Failed to write anchor " + generatedName + ": " + e.getMessage());
            }
        }
        return true;
    }
}