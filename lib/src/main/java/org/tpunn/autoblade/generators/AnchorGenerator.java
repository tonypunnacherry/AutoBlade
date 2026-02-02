package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.Anchor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;

public class AnchorGenerator {
    private final ProcessingEnvironment env;
    public AnchorGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return;
        for (Element e : roundEnv.getElementsAnnotatedWith(Anchor.class)) {
            if (!(e instanceof TypeElement te)) continue;
            String pkg = env.getElementUtils().getPackageOf(te).getQualifiedName().toString();
            Anchor anchor = te.getAnnotation(Anchor.class);
            String simpleName = te.getSimpleName().toString();
            String prefix = !anchor.value().isEmpty() ? anchor.value() :
                    (simpleName.endsWith("Anchor") ? simpleName.substring(0, simpleName.lastIndexOf("Anchor")) : simpleName);

            TypeSpec spec = TypeSpec.annotationBuilder("Auto" + prefix + "Anchor")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(ClassName.get("javax.inject", "Scope")) // Pure Scope only
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang.annotation", "Retention"))
                            .addMember("value", "$T.RUNTIME", ClassName.get("java.lang.annotation", "RetentionPolicy")).build())
                    .build();

            try { JavaFile.builder(pkg, spec).build().writeTo(env.getFiler()); } catch (IOException ignored) {}
        }
    }
}
