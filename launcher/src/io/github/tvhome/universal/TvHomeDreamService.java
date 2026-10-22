package io.github.tvhome.universal;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/** The system handles idle timing and dismissal; same scene as the manual screensaver. */
public class TvHomeDreamService extends DreamService {
    private ScreensaverScene scene;
    private static TvHomeDreamService active;
    static void dismissActive(){TvHomeDreamService dream=active;if(dream!=null)dream.wakeUp();}
    @Override public void onAttachedToWindow(){
        super.onAttachedToWindow();active=this;setInteractive(false);setFullscreen(true);setScreenBright(false);
        scene=new ScreensaverScene(this);setContentView(scene.view());
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        NavigationSound.key(this,event);
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onDreamingStarted(){super.onDreamingStarted();if(scene!=null)scene.start();}
    @Override public void onDreamingStopped(){if(scene!=null)scene.stop();super.onDreamingStopped();}
    @Override public void onDetachedFromWindow(){if(active==this)active=null;if(scene!=null){scene.stop();scene=null;}super.onDetachedFromWindow();}
}
