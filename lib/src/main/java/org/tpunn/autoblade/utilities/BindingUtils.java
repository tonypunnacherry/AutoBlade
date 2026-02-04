package org.tpunn.autoblade.utilities;

import com.squareup.javapoet.ClassName;
import org.tpunn.autoblade.annotations.Id;

import java.util.Optional;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public final class BindingUtils {
    private BindingUtils() {}

    /** Parses the base name for a Blade (e.g., "UserBladeContract" -> "User") */
    public static String parseBladeName(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        if (returnType.getKind() != TypeKind.DECLARED) return "Unknown";
        String simple = returnType.toString();
        simple = simple.substring(simple.lastIndexOf('.') + 1);
        return simple.replace("Contract", "").replace("Blade", "");
    }

    /** Finds the method or field annotated with @Id in a Record or class */
    public static String resolveIdAccessor(TypeElement record) {
        // Search record components first (Java 17+)
        Optional<String> recordId = record.getRecordComponents().stream()
                .filter(rc -> rc.getAnnotation(Id.class) != null)
                .map(rc -> rc.getSimpleName().toString())
                .findFirst();
        
        if (recordId.isPresent()) return recordId.get();

        // Fallback to standard methods/fields
        return record.getEnclosedElements().stream()
                .filter(e -> e.getAnnotation(Id.class) != null)
                .findFirst()
                .map(e -> e.getSimpleName().toString())
                .orElse("id"); // Default fallback
    }

    /** Safely returns ClassName for AppBlade to avoid direct dependency gridlock */
    public static ClassName getAppBlade(String pkg) {
        return ClassName.get(pkg, "AppBlade");
    }

    public static boolean hasAnnotation(Element element, String qualifiedName) {
        return element.getAnnotationMirrors().stream()
            .anyMatch(mirror -> mirror.getAnnotationType().toString().equals(qualifiedName));
    }

    public static String parseBladeNameFromRepo(TypeElement repo) {
        return repo.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                // Explicitly cast and map in one step to keep the type info clear
                .map(e -> (ExecutableElement) e)
                .filter(m -> hasAnnotation(m, "org.tpunn.autoblade.annotations.Create") 
                        || hasAnnotation(m, "org.tpunn.autoblade.annotations.Lookup"))
                // Use a block lambda to ensure the return type is explicitly String
                .map((ExecutableElement m) -> {
                    return parseBladeName(m);
                })
                .findFirst()
                .orElse("Unknown");
    }

}
