package org.tpunn.autoblade.utilities;

import java.util.Locale;

public final class NamingUtils {
    private NamingUtils() {}

    /**
     * Convert a string like "FOO_BAR" or "foo bar" to PascalCase -> "FooBar".
     */
    public static String toPascalCase(String s) {
        String[] parts = s.split("[_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return sb.toString();
    }
}
