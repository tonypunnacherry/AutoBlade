package org.tpunn.autoblade.utilities;

import org.tpunn.autoblade.annotations.Interface;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for selecting the most appropriate interface to use for DI bindings.
 */
public final class InterfaceSelector {
    private InterfaceSelector() {}

    public static TypeMirror selectBestInterface(TypeElement impl, ProcessingEnvironment env) {
        List<TypeMirror> ifaces = new ArrayList<>();
        String implType = impl.asType().toString();
        for (TypeMirror m : impl.getInterfaces()) {
            if (m == null) continue;
            String s = m.toString();
            // Skip common JDK interfaces and defensive: don't consider the implementation type itself
            if (s.startsWith("java.lang") || s.startsWith("java.io") || s.equals(implType)) continue;
            ifaces.add(m);
        }
        if (ifaces.isEmpty()) return null;
        if (ifaces.size() == 1) return ifaces.get(0);

        String implName = impl.getSimpleName().toString();

        // 0) explicit annotation: prefer interfaces annotated with @Interface
        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((javax.lang.model.type.DeclaredType) m).asElement();
            if (mElem.getAnnotation(Interface.class) != null) return m;
        }

        // 1) suffix match
        for (TypeMirror m : ifaces) {
            String iname = simpleNameOf(m);
            if (implName.endsWith(iname) || implName.endsWith(iname + "Impl") || implName.endsWith(iname + "Implementation")) {
                return m;
            }
        }

        // 2) stripped name match
        String base = stripImplSuffix(implName);
        for (TypeMirror m : ifaces) {
            if (simpleNameOf(m).equals(base)) return m;
        }

        // 3) same package
        String implPkg = env.getElementUtils().getPackageOf(impl).getQualifiedName().toString();
        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((javax.lang.model.type.DeclaredType) m).asElement();
            String mpkg = env.getElementUtils().getPackageOf(mElem).getQualifiedName().toString();
            if (mpkg.equals(implPkg)) return m;
        }

        // 4) fewest methods
        TypeMirror best = null;
        int bestCount = Integer.MAX_VALUE;
        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((javax.lang.model.type.DeclaredType) m).asElement();
            int count = 0;
            for (Element e : mElem.getEnclosedElements()) {
                if (e == null) continue;
                if (e.getKind() == ElementKind.METHOD) count++;
            }
            if (count < bestCount) {
                bestCount = count;
                best = m;
            }
        }
        return best;
    }

    private static String simpleNameOf(TypeMirror m) {
        return ((TypeElement) ((javax.lang.model.type.DeclaredType) m).asElement()).getSimpleName().toString();
    }

    private static String stripImplSuffix(String name) {
        String s = name;
        if (s.endsWith("Impl")) s = s.substring(0, s.length() - 4);
        if (s.endsWith("Implementation")) s = s.substring(0, s.length() - "Implementation".length());
        if (s.endsWith("Default")) s = s.substring(0, s.length() - "Default".length());
        return s;
    }

    public static TypeName selectBuilderInterface(String pkg, TypeMirror iface, TypeElement impl, ProcessingEnvironment env) {
        // For builders, resolve the builder name
        String builderInterfaceName = FactoryNaming.resolveName(iface, impl, env, "org.tpunn.autoblade.annotations.AutoBuilder", "Builder");
        if (builderInterfaceName == null) return null;
        return ClassName.get(pkg, builderInterfaceName);
    }

    public static TypeName selectFactoryInterface(String pkg, TypeMirror iface, TypeElement impl, ProcessingEnvironment env) {
        // For factories, resolve the factory name
        String factoryInterfaceName = FactoryNaming.resolveName(iface, impl, env, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");
        if (factoryInterfaceName == null) return null;
        return ClassName.get(pkg, factoryInterfaceName);
    }
}