package org.tpunn.autoblade.utilities;

import org.tpunn.autoblade.annotations.Interface;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
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
            if (s.startsWith("java.lang") || s.startsWith("java.io") || s.equals(implType)) continue;
            ifaces.add(m);
        }
        if (ifaces.isEmpty()) return null;
        if (ifaces.size() == 1) return ifaces.get(0);

        String implName = impl.getSimpleName().toString();

        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((DeclaredType) m).asElement();
            if (mElem.getAnnotation(Interface.class) != null) return m;
        }

        for (TypeMirror m : ifaces) {
            String iname = simpleNameOf(m);
            if (implName.endsWith(iname) || implName.endsWith(iname + "Impl") || implName.endsWith(iname + "Implementation")) {
                return m;
            }
        }

        String base = stripImplSuffix(implName);
        for (TypeMirror m : ifaces) {
            if (simpleNameOf(m).equals(base)) return m;
        }

        String implPkg = env.getElementUtils().getPackageOf(impl).getQualifiedName().toString();
        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((DeclaredType) m).asElement();
            String mpkg = env.getElementUtils().getPackageOf(mElem).getQualifiedName().toString();
            if (mpkg.equals(implPkg)) return m;
        }

        TypeMirror best = null;
        int bestCount = Integer.MAX_VALUE;
        for (TypeMirror m : ifaces) {
            TypeElement mElem = (TypeElement) ((DeclaredType) m).asElement();
            int count = 0;
            for (Element e : mElem.getEnclosedElements()) {
                if (e != null && e.getKind() == ElementKind.METHOD) count++;
            }
            if (count < bestCount) {
                bestCount = count;
                best = m;
            }
        }
        return best;
    }

    private static String simpleNameOf(TypeMirror m) {
        return ((TypeElement) ((DeclaredType) m).asElement()).getSimpleName().toString();
    }

    private static String stripImplSuffix(String name) {
        String s = name;
        if (s.endsWith("Impl")) s = s.substring(0, s.length() - 4);
        if (s.endsWith("Implementation")) s = s.substring(0, s.length() - "Implementation".length());
        if (s.endsWith("Default")) s = s.substring(0, s.length() - "Default".length());
        return s;
    }

    // FIX: Updated to convert TypeMirror to TypeElement for FactoryNaming
    public static TypeName selectBuilderInterface(String pkg, TypeMirror iface, TypeElement impl, ProcessingEnvironment env) {
        TypeElement ifaceElem = (TypeElement) env.getTypeUtils().asElement(iface);
        String name = FactoryNaming.resolveName(ifaceElem, env, "?", "Builder");
        return name == null ? null : ClassName.get(pkg, name);
    }

    public static TypeName selectBuilderImpl(String pkg, TypeElement impl, ProcessingEnvironment env) {
        String name = FactoryNaming.resolveName(impl, env, "org.tpunn.autoblade.annotations.AutoBuilder", "Builder");
        return name == null ? null : ClassName.get(pkg, name);
    }

    // FIX: Updated to convert TypeMirror to TypeElement for FactoryNaming
    public static TypeName selectFactoryInterface(String pkg, TypeMirror iface, TypeElement impl, ProcessingEnvironment env) {
        TypeElement ifaceElem = (TypeElement) env.getTypeUtils().asElement(iface);
        String name = FactoryNaming.resolveName(ifaceElem, env, "?", "Factory");
        return name == null ? null : ClassName.get(pkg, name);
    }

    public static TypeName selectFactoryImpl(String pkg, TypeElement impl, ProcessingEnvironment env) {
        String name = FactoryNaming.resolveName(impl, env, "org.tpunn.autoblade.annotations.AutoFactory", "Factory");
        return name == null ? null : ClassName.get(pkg, name);
    }
}
