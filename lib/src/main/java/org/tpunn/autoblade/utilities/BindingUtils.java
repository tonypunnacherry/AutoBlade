package org.tpunn.autoblade.utilities;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.tpunn.autoblade.annotations.Id;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

public final class BindingUtils {
    private BindingUtils() {}

    /** Extracts the raw Blade type from Optional<T>, Set<T>, or T */
    public static TypeName extractBladeType(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        if (returnType.getKind() == TypeKind.DECLARED) {
            DeclaredType declared = (DeclaredType) returnType;
            // If it's a wrapper like Optional<PlayerBlade> or Set<PlayerBlade>
            if (!declared.getTypeArguments().isEmpty()) {
                return TypeName.get(declared.getTypeArguments().get(0));
            }
        }
        return TypeName.get(returnType);
    }

    /** Converts a Blade TypeName back to its Anchor string (e.g., PlayerBlade -> PLAYER) */
    public static String parseAnchorFromBladeName(TypeName bladeType) {
        String simpleName = (bladeType instanceof ClassName cn) 
            ? cn.simpleName() 
            : bladeType.toString().substring(bladeType.toString().lastIndexOf('.') + 1);

        // Normalize to lowercase to match the map keys
        return simpleName.replace("Blade", "")
                        .replace("Contract", "")
                        .replace("_Auto", "")
                        .toLowerCase(); 
    }

    /** Parses the base name for a Blade (e.g., "UserBladeContract" -> "User") */
    public static String parseBladeName(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        // If it's a collection/optional, get the inner type name
        if (returnType instanceof DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
            returnType = dt.getTypeArguments().get(0);
        }
        if (returnType == null) throw new IllegalArgumentException("Method must have a return type");
        String simple = returnType.toString();
        simple = simple.substring(simple.lastIndexOf('.') + 1);
        return simple.replace("Contract", "").replace("Blade", "").replace("_Auto", "");
    }

    public static TypeMirror resolveIdType(TypeElement seed) {
        Element idElement = findIdElement(seed);
        if (idElement == null) return seed.asType(); // Self-ID fallback
        
        return (idElement instanceof ExecutableElement m) 
            ? m.getReturnType() 
            : idElement.asType();
    }

    public static String resolveIdAccessor(TypeElement seed) {
        Element idElement = findIdElement(seed);
        if (idElement == null) return ""; // Self-ID: Use object itself

        String name = idElement.getSimpleName().toString();
        
        // Check if it's a Record Component or a Method - both need ()
        // Note: getKind().toString() check for RECORD_COMPONENT is most compatible
        boolean isMethodLike = idElement.getKind() == ElementKind.METHOD || 
                            idElement.getKind().toString().equals("RECORD_COMPONENT");
                            
        return isMethodLike ? name + "()" : name;
    }

    private static Element findIdElement(TypeElement seed) {
        // 1. Check Record Components (Java 16+)
        for (RecordComponentElement rc : seed.getRecordComponents()) {
            if (rc == null) continue;
            if (rc.getAnnotation(Id.class) != null) return rc;
        }
        // 2. Check Enclosed Elements (Fields/Methods)
        for (Element e : seed.getEnclosedElements()) {
            if (e == null) continue;
            if (e.getAnnotation(Id.class) != null) return e;
        }
        return null;
    }

    public static boolean hasAnnotation(Element element, String qualifiedName) {
        return element.getAnnotationMirrors().stream()
            .anyMatch(mirror -> mirror != null && mirror.getAnnotationType().toString().equals(qualifiedName));
    }

    public static String parseBladeNameFromRepo(TypeElement repo) {
        return repo.getEnclosedElements().stream()
                .filter(e -> e != null && e.getKind() == ElementKind.METHOD)
                .map(e -> (ExecutableElement) e)
                .filter(m -> hasAnnotation(m, "org.tpunn.autoblade.annotations.Create") 
                        || hasAnnotation(m, "org.tpunn.autoblade.annotations.Lookup"))
                .map(BindingUtils::parseBladeName)
                .findFirst()
                .orElse("Unknown");
    }
}
