package org.tpunn.autoblade.utilities;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.*;

public final class GeneratedPackageResolver {

    /**
     * Finds the absolute root package common to all managed types.
     * If elements are in different root domains (e.g., 'com.app' and 'org.lib'), 
     * it returns an empty string (the default package).
     */
    public static String computeGeneratedPackage(Collection<TypeElement> managed, ProcessingEnvironment env) {
        if (managed == null || managed.isEmpty()) return "";

        // Collect all unique package names as arrays of parts
        List<String[]> allPackageParts = managed.stream()
                .map(te -> env.getElementUtils().getPackageOf(te).getQualifiedName().toString())
                .distinct()
                .map(pkg -> pkg.isEmpty() ? new String[0] : pkg.split("\\."))
                .toList();

        if (allPackageParts.isEmpty()) return "";
        
        // Start with the first package as the candidate for the root
        String[] commonParts = allPackageParts.get(0);
        int commonLength = commonParts.length;

        for (int i = 1; i < allPackageParts.size(); i++) {
            String[] currentParts = allPackageParts.get(i);
            commonLength = Math.min(commonLength, currentParts.length);
            
            for (int j = 0; j < commonLength; j++) {
                if (!commonParts[j].equals(currentParts[j])) {
                    commonLength = j; // Mismatch found, truncate common path
                    break;
                }
            }
            if (commonLength == 0) break; // No common root exists
        }

        return String.join(".", Arrays.copyOfRange(commonParts, 0, commonLength));
    }

    /**
     * Directly gets the package of a specific element.
     * Useful for Dagger-native "origin-based" generation.
     */
    public static String getPackage(TypeElement element, ProcessingEnvironment env) {
        // 1. Primary: Use the standard Utils (Fast)
        PackageElement pkg = env.getElementUtils().getPackageOf(element);
        
        // 2. Backup: If Kapt returns a blank/unnamed package for a named Kotlin class,
        // manually traverse the hierarchy (The Dagger way).
        if (pkg == null || pkg.isUnnamed()) {
            Element enclosing = element;
            while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
                enclosing = enclosing.getEnclosingElement();
            }
            if (enclosing instanceof PackageElement fallbackPkg) {
                return fallbackPkg.getQualifiedName().toString();
            }
        }

        return pkg != null ? pkg.getQualifiedName().toString() : "";
    }
}
