package io.github.tvhome.universal;
import android.content.*;
import android.os.*;
import android.provider.Settings;
final class WakeSoundController {
    private static Context app;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static long lastSleepSound=-3000;
    private static boolean wakeScheduled;
    static void init(Context context){
        if(app!=null)return;app=context.getApplicationContext();
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        TvPlatform.register(app,new BroadcastReceiver(){
            public void onReceive(Context c,Intent intent){
                if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                    NavigationSound.screenSleeping();
                    app.getSharedPreferences("wake_sound",0).edit().putBoolean("sleeping",true)
                        .putLong("sleep_at",SystemClock.elapsedRealtime()).putInt("boot",boot()).apply();
                    sleepRequested();
                }else{NavigationSound.prepareWake();foreground();}
            }
        },filter);
    }
    private static int boot(){return Settings.Global.getInt(app.getContentResolver(),"boot_count",-1);}
    static void sleepRequested(){
        if(app==null)return;
        long now=SystemClock.uptimeMillis();if(now-lastSleepSound<2000)return;lastSleepSound=now;
        NavigationSound.event(app,NavigationSound.OPEN);
    }
    static void foreground(){
        if(app==null||wakeScheduled)return;
        PowerManager power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
        if(power==null||!power.isInteractive())return;
        SharedPreferences prefs=app.getSharedPreferences("wake_sound",0);
        if(!prefs.getBoolean("sleeping",false))return;
        NavigationSound.prepareWake();
        boolean valid=WakeSoundPolicy.valid(prefs.getInt("boot",-2),boot(),prefs.getLong("sleep_at",-1),SystemClock.elapsedRealtime());
        prefs.edit().putBoolean("sleeping",false).apply();
        if(!valid)return;
        wakeScheduled=true;handler.postDelayed(()->{
            wakeScheduled=false;
            if(power.isInteractive())NavigationSound.wake(app);
        },650);
    }
}
