package io.github.tvhome.universal;
final class GreetingText {
    static String suffix(String raw){
        String value=raw==null?"":raw.trim();
        value=value.replaceFirst("(?iu)^SELAMAT\\s+(PAGI|SIANG|SORE|MALAM)(?:[\\s,!:;.-]+|$)","").trim();
        return value.toUpperCase(java.util.Locale.getDefault());
    }
}
