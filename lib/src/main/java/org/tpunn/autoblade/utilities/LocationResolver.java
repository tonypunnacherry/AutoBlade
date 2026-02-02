package org.tpunn.autoblade.utilities;

import org.tpunn.autoblade.annotations.Anchor;
import javax.lang.model.element.*;

public class LocationResolver {
    public static String resolveLocation(TypeElement te) {
        for (AnnotationMirror mirror : te.getAnnotationMirrors()) {
            Element anno = mirror.getAnnotationType().asElement();
            Anchor anchorAnno = anno.getAnnotation(Anchor.class);
            if (anchorAnno != null) {
                return !anchorAnno.value().isEmpty()
                        ? anchorAnno.value()
                        : anno.getSimpleName().toString();
            }
            if (anno.getSimpleName().toString().equals("Singleton")) return "App";
        }
        return "App";
    }

    public static String resolveAnchorName(TypeElement te) {
        String loc = resolveLocation(te);
        return loc.equals("App") ? "Singleton" : "Auto" + loc + "Anchor";
    }
}