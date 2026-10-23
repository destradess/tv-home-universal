package io.github.tvhome.universal;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
final class WatchTimerController {
    private static Context app;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static final Runnable due=WatchTimerController::check;
    static void init(Context context){
        if(app!=null)return;app=context.getApplicationContext();
        TvPlatform.register(app,new BroadcastReceiver(){public void onReceive(Context c,Intent i){cancel();}},new IntentFilter(Intent.ACTION_SCREEN_OFF));
        if(!WatchTimerPolicy.valid(prefs().getInt("boot",-2),boot(),prefs().getLong("deadline",0)))cancel();
    }
    private static SharedPreferences prefs(){return app.getSharedPreferences("watch_timer",0);}
    private static int boot(){return Settings.Global.getInt(app.getContentResolver(),"boot_count",-1);}
    private static AlarmManager alarms(){return (AlarmManager)app.getSystemService(Context.ALARM_SERVICE);}
    private static PendingIntent alarm(){return PendingIntent.getBroadcast(app,7641,new Intent(app,WatchTimerReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static int duration(){return WatchTimerPolicy.minutes(prefs().getInt("minutes",60));}
    static void duration(int minutes){prefs().edit().putInt("minutes",WatchTimerPolicy.minutes(minutes)).apply();}
    static boolean active(){return WatchTimerPolicy.valid(prefs().getInt("boot",-2),boot(),prefs().getLong("deadline",0));}
    static long remaining(){return active()?WatchTimerPolicy.seconds(prefs().getLong("deadline",0),SystemClock.elapsedRealtime()):0;}
    static boolean alarmPermission(){
        AlarmManager manager=alarms();
        return manager!=null&&(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms());
    }
    static boolean start(int minutes){
        if(Build.VERSION.SDK_INT<28||!alarmPermission())return false;
        if(!LauncherOverrideService.isConnected()||boot()<0)return false;
        int value=WatchTimerPolicy.minutes(minutes);long end=SystemClock.elapsedRealtime()+value*60000L;
        cancel();
        prefs().edit().putInt("minutes",value).putInt("boot",boot()).putLong("deadline",end).apply();
        try{schedule(end);if(!LauncherOverrideService.updateWatchBadge())throw new IllegalStateException("Timer overlay unavailable");return true;}catch(RuntimeException e){cancel();return false;}
    }
    private static void schedule(long end){
        AlarmManager manager=alarms();if(manager==null)throw new IllegalStateException("Alarm service unavailable");
        // Android 12+ requires the user to allow exact alarms for this target SDK.
        manager.setExact(AlarmManager.ELAPSED_REALTIME,end,alarm());
        handler.removeCallbacks(due);handler.postDelayed(due,Math.max(1,end-SystemClock.elapsedRealtime()));
    }
    static void cancel(){
        if(app==null)return;
        handler.removeCallbacks(due);
        AlarmManager manager=alarms();if(manager!=null)manager.cancel(alarm());
        prefs().edit().remove("deadline").remove("countdown_end").apply();
        LauncherOverrideService.dismissWatchCountdown();
    }
    static void check(){
        if(app==null||!active())return;
        PowerManager power=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
        if(power==null||!power.isInteractive()){cancel();return;}
        long end=prefs().getLong("deadline",0),now=SystemClock.elapsedRealtime();
        if(now<end){try{schedule(end);if(!LauncherOverrideService.updateWatchBadge())throw new IllegalStateException("Timer overlay unavailable");}catch(RuntimeException e){failed();}return;}
        long countdown=prefs().getLong("countdown_end",0);
        if(countdown==0){countdown=now+30000;prefs().edit().putLong("countdown_end",countdown).apply();}
        if(!LauncherOverrideService.showWatchCountdown(countdown))failed();
    }
    static void expired(){
        if(!active())return;
        long end=prefs().getLong("countdown_end",0);
        if(end<=0||SystemClock.elapsedRealtime()<end)return;
        sleepNow();
    }
    static void sleepNow(){
        if(!active())return;
        cancel();
        if(!LauncherOverrideService.sleepFromWatchTimer())failed();
    }
    private static void failed(){cancel();Toast.makeText(app,UiText.get(R.string.watch_failed),Toast.LENGTH_LONG).show();}
}
