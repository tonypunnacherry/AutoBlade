package org.tpunn.autoblade.validators;

import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.LocationResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;

public class Validator {
    private final ProcessingEnvironment env;

    public Validator(ProcessingEnvironment env) { this.env = env; }

    public void validate(RoundEnvironment roundEnv, Map<String, TypeElement> anchorMap) {
        validateSeeds(roundEnv);
        validateServices(roundEnv);
        validateRepositories(roundEnv, anchorMap);
    }

    private void validateSeeds(RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Seed.class)) {
            if (!(e instanceof TypeElement te)) continue;

            long idCount = (te.getKind() == ElementKind.RECORD) 
                ? te.getRecordComponents().stream().filter(rc -> rc.getAnnotation(Id.class) != null).count()
                : te.getEnclosedElements().stream().filter(el -> el.getKind() == ElementKind.FIELD && el.getAnnotation(Id.class) != null).count();

            if (idCount > 1) {
                error("Seed '" + te.getSimpleName() + "' cannot have multiple @Id annotations.", te);
            }
        }
    }

    private void validateServices(RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Scoped.class)) {
            if (e instanceof TypeElement te && te.getInterfaces().isEmpty()) {
                error("Scoped class '" + te.getSimpleName() + "' must implement a business interface.", te);
            }
        }
    }

    private void validateRepositories(RoundEnvironment roundEnv, Map<String, TypeElement> anchorMap) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Repository.class)) {
            TypeElement repo = (TypeElement) e;
            String repoLoc = LocationResolver.resolveLocation(repo).toLowerCase();

            for (Element enclosed : repo.getEnclosedElements()) {
                if (!(enclosed instanceof ExecutableElement method)) continue;

                boolean isCreate = BindingUtils.hasAnnotation(method, Create.class.getName());
                boolean isLookup = BindingUtils.hasAnnotation(method, Lookup.class.getName());
                if (!isCreate && !isLookup) continue;

                List<? extends VariableElement> params = method.getParameters();
                if (params.size() != 1) {
                    error("Method '" + method.getSimpleName() + "' must have exactly one parameter.", method);
                    continue;
                }

                validateSignature(method, params.get(0), repoLoc, anchorMap, isCreate, isLookup);
            }
        }
    }

    private void validateSignature(ExecutableElement m, VariableElement p, String repoLoc, 
                                   Map<String, TypeElement> anchorMap, boolean isCreate, boolean isLookup) {
        
        var targetBlade = BindingUtils.extractBladeType(m);
        String targetAnchor = BindingUtils.parseAnchorFromBladeName(targetBlade).toLowerCase();
        TypeElement seed = anchorMap.get(targetAnchor);

        if (seed == null) {
            error("No @Seed found for anchor [" + targetAnchor + "].", m);
            return;
        }

        TypeMirror pType = p.asType();
        TypeMirror idType = BindingUtils.resolveIdType(seed);
        TypeMirror sType = seed.asType();

        boolean matchesId = env.getTypeUtils().isSameType(pType, idType);
        boolean matchesSeed = env.getTypeUtils().isSameType(pType, sType);

        if (!matchesId && !matchesSeed) {
            error(String.format("Param mismatch. Expected Seed [%s] or ID [%s].", sType, idType), p);
        }

        if (isCreate && matchesId && !matchesSeed) {
            error("@Create methods must accept the full Seed object.", p);
        }
    }

    private void error(String msg, Element e) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, "AutoBlade: " + msg, e);
    }
}
