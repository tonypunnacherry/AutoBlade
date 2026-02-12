package org.tpunn.autoblade.utilities;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

public final class FactoryNaming {
    public static String resolveName(TypeElement element, ProcessingEnvironment env, String annotationFq, String fallbackSuffix) {
        return resolveName(InterfaceSelector.selectBestInterface(element, env), element, env, annotationFq, fallbackSuffix);
    }

    /**
     * Resolves the name using the best interface as the base prefix.
     * @param annotationFq The FQ name of the annotation, or "?" for a forced default.
     */
    public static String resolveName(TypeMirror bestInterface, TypeElement element, ProcessingEnvironment env, String annotationFq, String fallbackSuffix) {
        // 1. Get base name from the InterfaceSelector
        String baseName = env.getTypeUtils().asElement(bestInterface).getSimpleName().toString();

        // 2. Forced fallback (e.g. for implicit Factory names needed by a Builder)
        if ("?".equals(annotationFq)) {
            return baseName + fallbackSuffix;
        }

        TypeElement annotationTypeElement = env.getElementUtils().getTypeElement(annotationFq);
        if (annotationTypeElement == null) return null;

        // 3. Extract from AnnotationMirror if present
        return element.getAnnotationMirrors().stream()
                .findFirst()
                .map(mirror -> {
                    if (mirror == null) return null;
                    String named = "";
                    String suffix = "";
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                        var k = entry.getKey();
                        var v = entry.getValue();
                        if (k == null || v == null) continue;
                        String key = k.getSimpleName().toString();
                        Object val = v.getValue();
                        if ("named".equals(key)) named = val.toString();
                        else if ("suffix".equals(key)) suffix = val.toString();
                    }
                    if (!named.isEmpty()) return named;
                    if (!suffix.isEmpty()) return baseName + suffix;
                    return baseName + fallbackSuffix;
                })
                .orElse(null);
    }
}
