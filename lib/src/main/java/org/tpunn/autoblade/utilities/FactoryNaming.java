package org.tpunn.autoblade.utilities;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

public final class FactoryNaming {

    /**
     * Resolves the name using the best interface as the base prefix.
     */
    public static String resolveName(TypeElement element, ProcessingEnvironment env, String annotationFq, String fallbackSuffix) {
        TypeMirror bestInterface = InterfaceSelector.selectBestInterface(element, env);
        if (bestInterface == null) {
            // Fallback to the class name if no interface is found
            return element.getSimpleName().toString() + fallbackSuffix;
        }
        
        String baseName = env.getTypeUtils().asElement(bestInterface).getSimpleName().toString();

        // 1. Forced fallback (e.g. for shared Strategy interfaces)
        if ("?".equals(annotationFq)) {
            return baseName + fallbackSuffix;
        }

        // 2. Scan mirrors for the specific annotation (AutoFactory or AutoBuilder)
        return element.getAnnotationMirrors().stream()
                .filter(m -> m.getAnnotationType().toString().equals(annotationFq))
                .findFirst()
                .map(mirror -> {
                    String named = "";
                    String suffix = "";
                    
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                        String key = entry.getKey().getSimpleName().toString();
                        Object val = entry.getValue().getValue();
                        if ("named".equals(key)) named = val.toString();
                        else if ("suffix".equals(key)) suffix = val.toString();
                    }
                    
                    if (!named.isEmpty()) return named;
                    if (!suffix.isEmpty()) return baseName + suffix;
                    return baseName + fallbackSuffix;
                })
                // 3. SAFETY: If the annotation isn't found, return the standard fallback instead of null
                .orElse(baseName + fallbackSuffix);
    }
}
