package io.github.tvhome.universal;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

public class LauncherOverrideService extends AccessibilityService {
    static final HomeOverridePolicy POLICY = new HomeOverridePolicy();
    private static boolean connected;
    private static LauncherOverrideService current;
    private boolean consumingHome;
    private int remoteKey = -1;
    private QuickSettingsPanel quickPanel;
    private android.view.WindowManager panelWindows;
    private void closeQuickPanel() {
        QuickSettingsPanel old=quickPanel;quickPanel=null;
        if(old!=null){
            old.stop();
            try { panelWindows.removeViewImmediate(old.view()); }
            catch(RuntimeException error){Log.w("TVHomeRemote","Cannot remove quick panel",error);}
        }
    }
    private void toggleQuickPanel() {
        if(quickPanel!=null){closeQuickPanel();return;}
        try {
            android.content.Context themed=new android.view.ContextThemeWrapper(this,R.style.QuickPanel);
            QuickSettingsPanel panel=new QuickSettingsPanel(themed,this::closeQuickPanel);
            panelWindows=(android.view.WindowManager)getSystemService(WINDOW_SERVICE);
            android.view.WindowManager.LayoutParams params=new android.view.WindowManager.LayoutParams(
                -1,-1,android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT);
            params.dimAmount=0.12f;
            params.setTitle("TV Home Quick Settings");
            quickPanel=panel;panelWindows.addView(panel.view(),params);panel.start();
        } catch(RuntimeException error){closeQuickPanel();Log.w("TVHomeRemote","Cannot show accessibility quick panel",error);}
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("launcher_override", MODE_PRIVATE);
    }
    static boolean isEnabled(Context context) { return preferences(context).getBoolean("enabled", false); }
    static boolean isConnected() { return connected; }
    static boolean showPowerMenu(){
        LauncherOverrideService service=current;
        if(!connected||service==null)return false;
        service.closeQuickPanel();
        try{boolean shown=service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);if(shown)NavigationSound.event(service,NavigationSound.OPEN);return shown;}
        catch(RuntimeException error){return false;}
    }
    static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("enabled", enabled).apply();
        POLICY.returnToTvHome();
    }

    @Override protected void onServiceConnected(){current=this;connected=true;android.content.pm.ActivityInfo home=TvPlatform.systemHome(this);if(home!=null)POLICY.configure(home.packageName,home.name);}
    @Override public void onDestroy() {
        closeQuickPanel();
        if(current==this)current=null;
        connected = false;
        consumingHome = false;
        super.onDestroy();
    }
    @Override public void onInterrupt() { consumingHome = false; }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isEnabled(this) || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (event.getPackageName() == null || event.getClassName() == null) return;
        String pkg = event.getPackageName().toString();
        String activity = event.getClassName().toString();
        boolean realActivity = false;
        try { getPackageManager().getActivityInfo(new ComponentName(pkg, activity), 0); realActivity = true; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        if (POLICY.shouldRedirect(pkg, activity, realActivity, SystemClock.uptimeMillis())) openTvHome();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if((key==KeyEvent.KEYCODE_POWER||key==KeyEvent.KEYCODE_SLEEP)&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            if(power!=null&&power.isInteractive())WakeSoundController.sleepRequested();
            return false;
        }
        if (key != KeyEvent.KEYCODE_HOME) {
            if (!isEnabled(this)) return false;
            boolean supported = (TvPlatform.isTcl()&&(key==4062||key==4065||key==4001))
                    || key == KeyEvent.KEYCODE_GUIDE || key == KeyEvent.KEYCODE_TV
                    || key == KeyEvent.KEYCODE_NOTIFICATION;
            if (!supported) return false;
            Log.i("TVHomeRemote", "key=" + key + " action=" + event.getAction());
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) remoteKey = key;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && remoteKey == key) {
                remoteKey = -1;
                if (!event.isCanceled()) openRemoteTarget(key);
                return true;
            }
            return false;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) consumingHome = isEnabled(this);
            return consumingHome;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && consumingHome) {
            consumingHome = false;
            if (!event.isCanceled() && isEnabled(this)) {
                POLICY.homePressed(SystemClock.uptimeMillis());
                openTvHome();
            }
            return true;
        }
        return false;
    }

    private void openRemoteTarget(int key) {
        try {
            Intent target;
            if (key == KeyEvent.KEYCODE_NOTIFICATION) {
                toggleQuickPanel();
                return;
            } else if (key == 4062 || key == 4065) {
                String pkg = key == 4062 ? "com.netflix.ninja" : "com.google.android.youtube.tv";
                target = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (target == null) target = getPackageManager().getLaunchIntentForPackage(pkg);
                if (target == null) throw new IllegalStateException("App unavailable: " + pkg);
            } else {
                closeQuickPanel();
                if(!TvPlatform.openLiveTv(this))Log.i("TVHomeRemote","No Live TV handler available");
                return;
            }
            closeQuickPanel();
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            Log.w("TVHomeRemote", "Cannot open target for key=" + key, error);
        }
    }

    private void openTvHome() {
        closeQuickPanel();
        try {
            startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException error) { Log.w("TVHomeOverride", "Cannot open TV Home", error); }
    }
}
