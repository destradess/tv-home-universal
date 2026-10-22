package io.github.tvhome.universal;

/** Window identity only: never inspect accessibility node content. */
final class HomeOverridePolicy {
    private String systemPackage="";
    private String systemActivity="";
    void configure(String pkg,String activity){systemPackage=pkg;systemActivity=activity;}
    static final String SELF = "io.github.tvhome.universal";
    private boolean googleVisit;
    private long lastRedirect = -10000;

    void allowGoogleVisit() { googleVisit = true; }
    void returnToTvHome() { googleVisit = false; }
    void homePressed(long now) { googleVisit = false; lastRedirect = now; }

    boolean shouldRedirect(String pkg, String activity, boolean realActivity, long now) {
        if (googleVisit) {
            if (realActivity && !systemPackage.equals(pkg) && !SELF.equals(pkg)
                    && !"android".equals(pkg) && !"com.android.systemui".equals(pkg)) {
                googleVisit = false;
            }
            return false;
        }
        if (!systemPackage.equals(pkg) || !systemActivity.equals(activity)) return false;
        if (now - lastRedirect < 1000) return false;
        lastRedirect = now;
        return true;
    }
}
