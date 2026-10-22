package io.github.tvhome.universal;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import java.util.WeakHashMap;
final class NavigationSound {
    static final int NAV=0,CONFIRM=1,BACK=2,LAUNCH=3,OPEN=4,CLOSE=5,SIDE_OPEN=6,SIDE_CLOSE=7,ON=8,OFF=9,TAB=10,VOLUME=11,BUMP=12;
    private static final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pendingKey;
    private static int lastKind=-1;
    private static long wakeProtectedUntil;
    private static long lastNavigationInput=-1000;
    private static boolean screenAsleep;
    private static final WeakHashMap<View,Boolean> attached=new WeakHashMap<>();
    private static final WeakHashMap<Window,Boolean> windows=new WeakHashMap<>();
    private static long lastSound,lastAction,lastKeyTime=-1;
    private static int lastKey=-1,stream;
    private static SoundPool pool;
    private static android.content.SharedPreferences settings;
    private static android.content.SharedPreferences prefs(Context context){
        if(settings==null)settings=context.getApplicationContext().getSharedPreferences("navigation_sound",0);
        return settings;
    }
    private static String loadedPack="";
    private static boolean previewPending;
    private static final int[] sounds=new int[13];
    private static final boolean[] ready=new boolean[13];
    static boolean enabled(Context context){return prefs(context).getBoolean("enabled",true);}
    static void setEnabled(Context context,boolean enabled){prefs(context).edit().putBoolean("enabled",enabled).apply();if(!enabled&&pool!=null)pool.autoPause();}
    static int volume(Context context){return Math.max(0,Math.min(100,prefs(context).getInt("volume",70)));}
    static void setVolume(Context context,int volume){prefs(context).edit().putInt("volume",Math.max(0,Math.min(100,volume))).apply();}
    static String selected(Context context){
        String id=prefs(context).getString("pack","ps2");
        return SoundPacks.IDS[SoundPacks.index(id)];
    }
    static void select(Context context,String id){
        String valid=SoundPacks.IDS[SoundPacks.index(id)];
        prefs(context).edit().putString("pack",valid).apply();
        init(context);previewPending=true;
        if(ready[0]){previewPending=false;play(context);}
    }
    static void init(Context context){
        String selected=selected(context);
        if(pool!=null&&selected.equals(loadedPack))return;
        if(pool!=null){pool.release();pool=null;}
        loadedPack=selected;stream=0;previewPending=false;
        java.util.Arrays.fill(ready,false);java.util.Arrays.fill(sounds,0);
        Context app=context.getApplicationContext();
        try{
            pool=new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((p,id,status)->{
                if(p!=pool)return;
                for(int i=0;i<sounds.length;i++)if(sounds[i]==id)ready[i]=status==0;
                if(ready[0]&&previewPending){previewPending=false;play(app);}
            });
            int[] resources=SoundPacks.RESOURCES[SoundPacks.index(selected)];
            for(int i=0;i<resources.length;i++)sounds[i]=pool.load(app,resources[i],1);
        }catch(RuntimeException ignored){}
    }
    static void attach(View root){
        if(attached.containsKey(root))return;
        attached.put(root,true);root.setSoundEffectsEnabled(false);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((previous,current)->{
            if(current!=null)current.setSoundEffectsEnabled(false);
            if(previous==null||current==null||previous==current||!current.isShown()||!current.hasWindowFocus())return;
            long now=SystemClock.uptimeMillis();
            if(!screenAsleep&&now-lastNavigationInput>=0&&now-lastNavigationInput<250)play(current.getContext());
        });
    }
    static LinearLayout panel(Context context){
        return new LinearLayout(context){
            @Override public boolean dispatchKeyEvent(KeyEvent event){key(getContext(),event,findFocus());return super.dispatchKeyEvent(event);}
        };
    }
    static void attachWindow(Window window){
        if(window==null||windows.containsKey(window))return;
        Window.Callback original=window.getCallback();if(original==null)return;
        Context context=window.getContext().getApplicationContext();
        Window.Callback proxy=(Window.Callback)java.lang.reflect.Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(object,method,args)->{
            if("dispatchKeyEvent".equals(method.getName())&&args!=null&&args.length==1&&args[0] instanceof KeyEvent)key(context,(KeyEvent)args[0],window.getDecorView().findFocus());
            try{return method.invoke(original,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
        });
        window.setCallback(proxy);windows.put(window,true);
    }
    static void key(Context context,KeyEvent event){key(context,event,null);}
    static void key(Context context,KeyEvent event,View focus){
        int direction=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(direction==KeyEvent.KEYCODE_DPAD_LEFT||direction==KeyEvent.KEYCODE_DPAD_RIGHT||direction==KeyEvent.KEYCODE_DPAD_UP||direction==KeyEvent.KEYCODE_DPAD_DOWN))lastNavigationInput=SystemClock.uptimeMillis();
        if(focus instanceof android.widget.SeekBar&&event.getAction()==KeyEvent.ACTION_DOWN&&
                (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){
            event(context,VOLUME);return;
        }
        if(event.getAction()!=KeyEvent.ACTION_UP||event.isCanceled())return;
        int code=event.getKeyCode();
        if(code!=KeyEvent.KEYCODE_BACK&&code!=KeyEvent.KEYCODE_DPAD_CENTER&&code!=KeyEvent.KEYCODE_ENTER&&code!=KeyEvent.KEYCODE_NUMPAD_ENTER)return;
        if(lastKeyTime==event.getEventTime()&&lastKey==code)return;
        lastKeyTime=event.getEventTime();lastKey=code;
        if(code!=KeyEvent.KEYCODE_BACK&&focus!=null&&Boolean.TRUE.equals(focus.getTag(R.id.sound_app_card)))return;
        final int kind=code==KeyEvent.KEYCODE_BACK?BACK:focus instanceof android.widget.Switch?
                (((android.widget.Switch)focus).isChecked()?OFF:ON):CONFIRM;
        if(pendingKey!=null)handler.removeCallbacks(pendingKey);
        pendingKey=()->{pendingKey=null;play(context,kind);};handler.postDelayed(pendingKey,35);
    }
    static void event(Context context,int kind){
        if(pendingKey!=null){handler.removeCallbacks(pendingKey);pendingKey=null;}
        play(context,kind);
    }
    static void screenSleeping(){screenAsleep=true;lastNavigationInput=-1000;}
    static void prepareWake(){screenAsleep=false;lastNavigationInput=-1000;wakeProtectedUntil=SystemClock.uptimeMillis()+1800;}
    static void wake(Context context){event(context,LAUNCH);}
    static void play(Context context){play(context,NAV);}
    private static void play(Context context,int kind){
        if(!enabled(context)||volume(context)==0)return;
        long now=SystemClock.uptimeMillis();
        if(kind==NAV&&(screenAsleep||now-lastSound<70||now-lastAction<120||now<wakeProtectedUntil))return;
        if(kind==lastKind&&now-lastSound<70)return;
        lastSound=now;lastKind=kind;if(kind!=NAV)lastAction=now;
        try{
            init(context);float gain=volume(context)/100f;
            if(pool!=null&&ready[kind]){
                if(stream!=0)pool.stop(stream);
                stream=pool.play(sounds[kind],gain,gain,1,0,1f);
            }
        }catch(RuntimeException ignored){}
    }
}
