package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;

public class ScopeGenerator {
    private final ProcessingEnvironment env;
    public ScopeGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return;
        for (Element e : roundEnv.getElementsAnnotatedWith(Seed.class)) {
            if (!(e instanceof TypeElement seed)) continue;
            String loc = LocationResolver.resolveLocation(seed);
            if ("App".equals(loc)) continue;
            String pkg = env.getElementUtils().getPackageOf(seed).getQualifiedName().toString();
            ClassName compName = ClassName.get(pkg, loc + "Component");

            TypeSpec.Builder comp = TypeSpec.interfaceBuilder(compName).addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"))
                            .addMember("modules", "$T.class", ClassName.get(pkg, loc + "AutoModule")).build());

            // Annotate Subcomponent with the generated Scope
            seed.getAnnotationMirrors().stream().map(m -> m.getAnnotationType().asElement())
                    .filter(el -> el.getAnnotation(Anchor.class) != null).findFirst().ifPresent(el -> {
                        Anchor a = el.getAnnotation(Anchor.class);
                        String name = el.getSimpleName().toString();
                        String prefix = !a.value().isEmpty() ? a.value() : (name.endsWith("Anchor") ? name.substring(0, name.lastIndexOf("Anchor")) : name);
                        comp.addAnnotation(ClassName.get(pkg, "Auto" + prefix + "Anchor"));
                    });

            roundEnv.getElementsAnnotatedWith(EntryPoint.class).stream()
                    .filter(en -> en instanceof TypeElement te && loc.equals(LocationResolver.resolveLocation(te)))
                    .forEach(te -> comp.addMethod(ProvisionMethod.generateProvisionMethod((TypeElement) te)));

            comp.addType(TypeSpec.interfaceBuilder("Builder").addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                    .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
                    .addMethod(MethodSpec.methodBuilder("seed").addAnnotation(ClassName.get("dagger", "BindsInstance"))
                            .addParameter(TypeName.get(seed.asType()), "data").returns(compName.nestedClass("Builder")).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build())
                    .addMethod(MethodSpec.methodBuilder("build").returns(compName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).build()).build());

            try { JavaFile.builder(pkg, comp.build()).build().writeTo(env.getFiler()); } catch (IOException ignored) {}
        }
    }
}
