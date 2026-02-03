package org.tpunn.autoblade.utilities;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.*;

/**
 * Utility to determine the package to use for generated sources.
 */
public final class GeneratedPackageResolver {
/**
     * Finds the base parent package common to all managed types.
     */
    public static String computeGeneratedPackage(Collection<TypeElement> managed, ProcessingEnvironment env) {
        if (managed == null || managed.isEmpty()) return "";

        String common = null;
        for (TypeElement te : managed) {
            String current = env.getElementUtils().getPackageOf(te).getQualifiedName().toString();
            if (common == null) {
                common = current;
            } else {
                common = findCommonPath(common, current);
            }
        }
        return common;
    }

    private static String findCommonPath(String p1, String p2) {
        String[] parts1 = p1.split("\\.");
        String[] parts2 = p2.split("\\.");
        int minLength = Math.min(parts1.length, parts2.length);
        
        int i = 0;
        while (i < minLength && parts1[i].equals(parts2[i])) {
            i++;
        }

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < i; j++) {
            if (j > 0) sb.append(".");
            sb.append(parts1[j]);
        }
        return sb.toString();
    }
}
