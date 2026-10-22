package io.github.tvhome.universal;
import java.util.Locale;
final class AnimeTitleRules {
    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    static boolean matches(String candidate,String[] aliases) {
        String normalized=key(candidate);
        if(normalized.isEmpty())return false;
        for(String alias:aliases)if(normalized.equals(key(alias)))return true;
        return false;
    }
}
