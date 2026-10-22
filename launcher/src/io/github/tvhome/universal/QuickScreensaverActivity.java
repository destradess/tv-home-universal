package io.github.tvhome.universal;

import android.app.Activity;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public class QuickScreensaverActivity extends Activity {
    private ScreensaverScene scene;
    private static final Set<QuickScreensaverActivity> instances=new LinkedHashSet<>();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        instances.add(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams wp=getWindow().getAttributes();wp.screenBrightness=0.12f;getWindow().setAttributes(wp);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        scene=new ScreensaverScene(this);scene.view().setOnClickListener(v->exitScreensaver());setContentView(scene.view());
    }
    @Override protected void onResume(){super.onResume();scene.start();}
    @Override protected void onPause(){scene.stop();super.onPause();}
    @Override protected void onDestroy(){
        instances.remove(this);
        if(scene!=null)scene.stop();
        super.onDestroy();
    }
    private void exitScreensaver(){
        TvHomeDreamService.dismissActive();
        for(QuickScreensaverActivity saver:new ArrayList<>(instances)){
            if(!saver.isFinishing())saver.finish();
        }
    }
    @Override public void onBackPressed(){exitScreensaver();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())exitScreensaver();
            return true;
        }
        if(event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_UP||event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(event);
        if(event.getAction()==KeyEvent.ACTION_UP)exitScreensaver();return true;
    }
}
