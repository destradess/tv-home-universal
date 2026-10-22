package io.github.tvhome.universal;

final class MovieBackdropRules {
    static final long REFRESH_MS = 24L * 60 * 60 * 1000;
    static boolean supportedMedia(String mediaType) {
        return "movie".equals(mediaType) || "tv".equals(mediaType);
    }
    static String title(String mediaType, String title, String name, String originalTitle, String originalName) {
        String[] choices = "tv".equals(mediaType) ? new String[]{name, originalName, title, originalTitle}
                : new String[]{title, originalTitle, name, originalName};
        for (String choice : choices) if (choice != null && !choice.trim().isEmpty()) return choice;
        return "Tontonan";
    }
    static boolean validPath(String path) {
        return path != null && path.matches("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png)");
    }
    static boolean isApiKey(String credential) { return credential.matches("[a-fA-F0-9]{32}"); }
    static String normalizeCredential(String value) {
        String token = value.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        if (token.length() < 20 || token.length() > 4096 || token.matches("(?s).*\\s.*"))
            throw new IllegalArgumentException("Token/key TMDB tidak valid.");
        return token;
    }
    static boolean expired(long fetched, long now) {
        return fetched <= 0 || now < fetched || now - fetched >= REFRESH_MS;
    }
}
