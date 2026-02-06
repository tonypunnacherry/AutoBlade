package org.tpunn.autoblade.validators;

import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.utilities.BindingUtils;
import org.tpunn.autoblade.utilities.InterfaceSelector;
import org.tpunn.autoblade.utilities.LocationResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validator {
    private final ProcessingEnvironment env;

    public Validator(ProcessingEnvironment env) { this.env = env; }

    public void validate(RoundEnvironment roundEnv, Map<String, TypeElement> anchorMap) {
        validateSeeds(roundEnv);
        validateServices(roundEnv);
        validateRepositories(roundEnv, anchorMap);
        validateStrategies(roundEnv);
    }

    private void validateSeeds(RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Seed.class)) {
            if (!(e instanceof TypeElement te)) continue;

            long idCount = (te.getKind() == ElementKind.RECORD) 
                ? te.getRecordComponents().stream().filter(rc -> rc != null && rc.getAnnotation(Id.class) != null).count()
                : te.getEnclosedElements().stream().filter(el -> el != null && el.getKind() == ElementKind.FIELD && el.getAnnotation(Id.class) != null).count();

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
            if (repo == null) continue;
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

    private void validateStrategies(RoundEnvironment roundEnv) {
        // Map<InterfaceQualifiedName, FirstFoundAnchorName>
        Map<String, String> interfaceToAnchor = new java.util.HashMap<>();

        // We scan for all classes that are implementations of a Strategy
        for (Element e : roundEnv.getElementsAnnotatedWithAny(Set.of(Scoped.class, Transient.class))) {
            if (!(e instanceof TypeElement te)) continue;

            // Check if this specific class is marked with a @Strategy meta-annotation
            boolean isStrategy = te.getAnnotationMirrors().stream()
                .anyMatch(m -> m != null && m.getAnnotationType().asElement().getAnnotation(Strategy.class) != null);

            if (!isStrategy) continue;

            // Determine the business interface and the current anchor
            TypeMirror businessIface = InterfaceSelector.selectBestInterface(te, env);
            if (businessIface == null) continue;

            String ifaceName = businessIface.toString();
            String currentAnchor = LocationResolver.resolveLocation(te);

            if (interfaceToAnchor.containsKey(ifaceName)) {
                String existingAnchor = interfaceToAnchor.get(ifaceName);
                if (!existingAnchor.equals(currentAnchor)) {
                    error(String.format(
                        "Strategy dispersion detected! Interface [%s] has implementations in both [%s] and [%s]. " +
                        "All strategies for a single interface must belong to the same anchor.",
                        ifaceName, existingAnchor, currentAnchor), te);
                }
            } else {
                interfaceToAnchor.put(ifaceName, currentAnchor);
            }
        }
    }

    private void error(String msg, Element e) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, "AutoBlade: " + msg, e);
    }
}
