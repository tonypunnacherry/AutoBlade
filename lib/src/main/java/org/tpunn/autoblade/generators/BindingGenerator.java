package org.tpunn.autoblade.generators;

import com.squareup.javapoet.*;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.LocationResolver;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.*;

public class BindingGenerator {
    private final ProcessingEnvironment env;
    public BindingGenerator(ProcessingEnvironment env) { this.env = env; }

    public void generate(RoundEnvironment roundEnv) {
        Set<Element> managed = new HashSet<>(roundEnv.getElementsAnnotatedWith(Scoped.class));
        managed.addAll(roundEnv.getElementsAnnotatedWith(EntryPoint.class));
        if (managed.isEmpty()) return;

        Map<String, List<TypeElement>> groups = new HashMap<>();
        String pkg = env.getElementUtils().getPackageOf(managed.iterator().next()).getQualifiedName().toString();

        for (Element e : managed) {
            if (!(e instanceof TypeElement te)) continue;
            String loc = LocationResolver.resolveLocation(te);
            groups.computeIfAbsent(loc == null || loc.isEmpty() ? "App" : loc, k -> new ArrayList<>()).add(te);
        }
        groups.putIfAbsent("App", new ArrayList<>());

        groups.forEach((loc, svcs) -> {
            TypeSpec.Builder mod = TypeSpec.classBuilder(loc + "AutoModule")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).addAnnotation(ClassName.get("dagger", "Module"));

            for (TypeElement te : svcs) {
                te.getInterfaces().stream()
                        .filter(i -> !i.toString().startsWith("java.lang") && !i.toString().startsWith("java.io"))
                        .findFirst().ifPresent(iface -> {
                            MethodSpec.Builder bind = MethodSpec.methodBuilder("bind" + te.getSimpleName())
                                    .addAnnotation(ClassName.get("dagger", "Binds")).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                    .returns(TypeName.get(iface)).addParameter(TypeName.get(te.asType()), "impl");

                            if ("App".equals(loc)) bind.addAnnotation(ClassName.get("javax.inject", "Singleton"));
                            else bind.addAnnotation(getGeneratedAnchor(te, pkg));

                            mod.addMethod(bind.build());
                        });
            }
            try { JavaFile.builder(pkg, mod.build()).build().writeTo(env.getFiler()); } catch (IOException ignored) {}
        });
    }

    private ClassName getGeneratedAnchor(TypeElement te, String pkg) {
        return te.getAnnotationMirrors().stream().map(m -> m.getAnnotationType().asElement())
                .filter(e -> e.getAnnotation(Anchor.class) != null).findFirst().map(e -> {
                    Anchor a = e.getAnnotation(Anchor.class);
                    String name = e.getSimpleName().toString();
                    String prefix = !a.value().isEmpty() ? a.value() : (name.endsWith("Anchor") ? name.substring(0, name.lastIndexOf("Anchor")) : name);
                    return ClassName.get(pkg, "Auto" + prefix + "Anchor");
                }).orElse(ClassName.get("javax.inject", "Singleton"));
    }
}
