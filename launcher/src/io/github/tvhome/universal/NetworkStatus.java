package io.github.tvhome.universal;

final class NetworkStatus {
    static String label(String transport, boolean validated, boolean captivePortal) {
        if (validated) return "Online · " + transport;
        if (captivePortal) return "Perlu login · " + transport;
        // A missing validation result is not proof that internet access is unavailable.
        return "Terhubung · " + transport;
    }
}
