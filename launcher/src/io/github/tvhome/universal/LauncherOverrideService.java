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

    private android.widget.TextView watchBadge;
    private final Runnable badgeTick=new Runnable(){public void run(){
        if(watchBadge==null)return;
        if(!WatchTimerController.active()){removeWatchBadge();return;}
        long seconds=WatchTimerController.remaining();
        if(seconds==0){removeWatchBadge();WatchTimerController.check();return;}
        watchBadge.setText(seconds>=3600
            ?String.format(UiText.locale(),"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
            :String.format(UiText.locale(),"%02d:%02d",seconds/60,seconds%60));
        watchHandler.postDelayed(this,1000);
    }};
    static boolean updateWatchBadge(){
        LauncherOverrideService service=current;
        return connected&&service!=null&&service.addWatchBadge();
    }
    private boolean addWatchBadge(){
        if(watchOverlay!=null)return true;
        if(watchBadge!=null)return true;
        try{
            Context themed=new android.view.ContextThemeWrapper(this,R.style.QuickPanel);
            android.widget.TextView badge=new android.widget.TextView(themed);
            float density=getResources().getDisplayMetrics().density;
            badge.setTextSize(13);badge.setTextColor(0xEFFFFFFF);
            badge.setFontFeatureSettings("tnum");badge.setShadowLayer(3*density,0,density,0x99000000);
            badge.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
            badge.setGravity(android.view.Gravity.CENTER);badge.setSingleLine(true);
            badge.setPadding(Math.round(2*density),Math.round(2*density),Math.round(2*density),Math.round(2*density));
            badge.setBackground(null);
            android.view.WindowManager.LayoutParams params=new android.view.WindowManager.LayoutParams(-2,-2,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity=android.view.Gravity.TOP|android.view.Gravity.RIGHT;
            params.x=Math.round(16*density);params.y=Math.round(16*density);params.setTitle(UiText.get(R.string.watch_title));
            ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).addView(badge,params);watchBadge=badge;
            watchHandler.removeCallbacks(badgeTick);badgeTick.run();return true;
        }catch(RuntimeException error){removeWatchBadge();Log.w("TVHomeWatchTimer","Timer badge failed",error);return false;}
    }
    private void removeWatchBadge(){
        watchHandler.removeCallbacks(badgeTick);
        if(watchBadge!=null){
            try{((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(watchBadge);}catch(RuntimeException ignored){}
            watchBadge=null;
        }
    }

    private android.view.View watchOverlay;
    private android.widget.TextView watchSeconds;
    private long watchEnd;
    private final android.os.Handler watchHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable watchTick=new Runnable(){public void run(){
        if(watchOverlay==null)return;
        long seconds=WatchTimerPolicy.seconds(watchEnd,SystemClock.elapsedRealtime());
        if(seconds==0){WatchTimerController.expired();return;}
        watchSeconds.setText(String.format(UiText.locale(),UiText.get(R.string.watch_countdown),seconds));
        watchHandler.postDelayed(this,Math.min(1000,Math.max(1,watchEnd-SystemClock.elapsedRealtime())));
    }};
    static void dismissWatchCountdown(){if(current!=null){current.removeWatchCountdown();current.removeWatchBadge();}}
    private void removeWatchCountdown(){
        watchHandler.removeCallbacks(watchTick);
        if(watchOverlay!=null){
            try{((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(watchOverlay);}catch(RuntimeException ignored){}
            watchOverlay=null;watchSeconds=null;
        }
    }
    static boolean showWatchCountdown(long end){
        LauncherOverrideService service=current;
        return connected&&service!=null&&service.addWatchCountdown(end);
    }
    private boolean addWatchCountdown(long end){
        removeWatchBadge();
        if(watchOverlay!=null)return true;
        try{
            closeQuickPanel();watchEnd=end;
            Context themed=new android.view.ContextThemeWrapper(this,R.style.QuickPanel);
            android.widget.FrameLayout root=new android.widget.FrameLayout(themed){
                @Override public boolean dispatchKeyEvent(KeyEvent event){
                    NavigationSound.key(getContext(),event,findFocus());
                    if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
                        if(event.getAction()==KeyEvent.ACTION_UP)WatchTimerController.cancel();return true;
                    }
                    return super.dispatchKeyEvent(event);
                }
            };
            root.setBackgroundColor(0x99000000);
            float density=getResources().getDisplayMetrics().density;
            android.widget.LinearLayout panel=NavigationSound.panel(themed);
            panel.setOrientation(android.widget.LinearLayout.VERTICAL);panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            int pad=Math.round(24*density);panel.setPadding(pad,pad,pad,pad);panel.setBackground(GlassMenu.background(themed));
            android.widget.TextView title=new android.widget.TextView(themed);
            title.setText(UiText.get(R.string.watch_title));title.setTextColor(0xBBFFFFFF);title.setTextSize(13);
            title.setGravity(android.view.Gravity.CENTER);title.setTypeface(android.graphics.Typeface.create("sans-serif",0));
            android.widget.LinearLayout.LayoutParams titleParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
            titleParams.bottomMargin=Math.round(14*density);panel.addView(title,titleParams);
            watchSeconds=new android.widget.TextView(themed);watchSeconds.setTextColor(0xFFFFFFFF);watchSeconds.setTextSize(18);
            watchSeconds.setGravity(android.view.Gravity.CENTER);watchSeconds.setMaxLines(2);watchSeconds.setFontFeatureSettings("tnum");
            watchSeconds.setTypeface(android.graphics.Typeface.create("sans-serif",0));watchSeconds.setLineSpacing(3*density,1f);
            watchSeconds.setMinHeight(Math.round(52*density));panel.addView(watchSeconds,new android.widget.LinearLayout.LayoutParams(-1,-2));
            android.widget.LinearLayout actions=new android.widget.LinearLayout(themed);
            actions.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.Button sleep=new android.widget.Button(themed);sleep.setText(UiText.get(R.string.watch_sleep));
            sleep.setTypeface(android.graphics.Typeface.create("sans-serif",0));sleep.setTextSize(13);sleep.setGravity(android.view.Gravity.CENTER);
            sleep.setOnClickListener(v->WatchTimerController.sleepNow());
            android.widget.Button cancel=new android.widget.Button(themed);cancel.setText(UiText.get(R.string.watch_cancel_action));
            cancel.setTypeface(android.graphics.Typeface.create("sans-serif",0));cancel.setTextSize(13);cancel.setGravity(android.view.Gravity.CENTER);
            cancel.setOnClickListener(v->WatchTimerController.cancel());
            android.widget.LinearLayout.LayoutParams sleepParams=new android.widget.LinearLayout.LayoutParams(0,Math.round(42*density),1);
            sleepParams.rightMargin=Math.round(6*density);actions.addView(sleep,sleepParams);
            android.widget.LinearLayout.LayoutParams cancelParams=new android.widget.LinearLayout.LayoutParams(0,Math.round(42*density),1);
            cancelParams.leftMargin=Math.round(6*density);actions.addView(cancel,cancelParams);
            android.widget.LinearLayout.LayoutParams actionsParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
            actionsParams.topMargin=Math.round(18*density);panel.addView(actions,actionsParams);
            GlassControls.apply(panel);GlassControls.focusOnlyRows(panel);
            NavigationSound.attach(root);
            root.addView(panel,new android.widget.FrameLayout.LayoutParams(Math.round(320*density),-2,android.view.Gravity.CENTER));
            android.view.WindowManager.LayoutParams params=new android.view.WindowManager.LayoutParams(-1,-1,android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);
            params.setTitle(UiText.get(R.string.watch_title));
            ((android.view.WindowManager)getSystemService(WINDOW_SERVICE)).addView(root,params);watchOverlay=root;
            cancel.requestFocus();watchHandler.removeCallbacks(watchTick);watchTick.run();return true;
        }catch(RuntimeException error){removeWatchCountdown();Log.w("TVHomeWatchTimer","Countdown overlay failed",error);return false;}
    }
    static boolean sleepFromWatchTimer(){
        if(android.os.Build.VERSION.SDK_INT<28)return false;
        LauncherOverrideService service=current;if(!connected||service==null)return false;
        try{WakeSoundController.sleepRequested();return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);}
        catch(RuntimeException error){Log.w("TVHomeWatchTimer","Sleep action failed",error);return false;}
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

    @Override protected void onServiceConnected(){current=this;connected=true;android.content.pm.ActivityInfo home=TvPlatform.systemHome(this);if(home!=null)POLICY.configure(home.packageName,home.name);WatchTimerController.check();}
    @Override public void onDestroy() {
        closeQuickPanel();
        removeWatchCountdown();removeWatchBadge();
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
