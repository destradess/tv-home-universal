package io.github.tvhome.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class ScreensaverSettingsActivity extends Activity {
    private SeekBar saver, sleep;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean pending,tracking;
    private final Runnable persist=()->{if(pending&&!tracking){pending=false;save();}};
    private void scheduleSave(){pending=true;handler.removeCallbacks(persist);if(!tracking)handler.postDelayed(persist,600);}
    @Override protected void onPause(){
        handler.removeCallbacks(persist);
        if(pending){pending=false;save();}
        super.onPause();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0,0,0,0);
        LinearLayout rolls=new LinearLayout(this);rolls.setOrientation(LinearLayout.VERTICAL);
        long saverMs=Settings.System.getLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,600000);
        long sleepMs=Settings.Secure.getLong(getContentResolver(),"sleep_timeout",1200000);
        saver=picker(rolls,UiText.get(R.string.ui_112),saverMs);
        sleep=picker(rolls,UiText.get(R.string.ui_121),sleepMs);
        panel.addView(rolls,new LinearLayout.LayoutParams(-1,-2));
        Button nativeSettings=new Button(this);nativeSettings.setText(UiText.get(R.string.native_screensaver));
        nativeSettings.setOnClickListener(v->TvPlatform.launch(this,TvPlatform.settings(Settings.ACTION_DREAM_SETTINGS)));
        panel.addView(nativeSettings,new LinearLayout.LayoutParams(-1,dp(44)));
        boolean editable=Settings.System.canWrite(this)&&checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==PackageManager.PERMISSION_GRANTED;
        saver.setEnabled(editable);sleep.setEnabled(editable);
        SettingsPopup.show(this,UiText.get(R.string.ui_113),panel,editable?saver:nativeSettings);
    }
    private SeekBar picker(LinearLayout parent,String label,long millis){
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_VERTICAL);
        column.setPadding(dp(14),0,dp(14),0);
        TextView value=text("",13);value.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        MenuIcon.attach(value,label.equals(UiText.get(R.string.ui_112))?"screensaver":"sleep",19,0xFFFFFFFF);
        column.addView(value,new LinearLayout.LayoutParams(-1,dp(36)));
        SeekBar slider=new SeekBar(this);
        slider.setMax(1439);
        slider.setProgress((int)Math.max(1,Math.min(1440,(millis+59999)/60000))-1);
        slider.setKeyProgressIncrement(1);
        slider.setFocusable(true);
        value.setText(label+": "+(slider.getProgress()+1)+UiText.get(R.string.ui_006));
        slider.setContentDescription(label+", "+(slider.getProgress()+1)+UiText.get(R.string.ui_006));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                value.setText(label+": "+(progress+1)+UiText.get(R.string.ui_006));
                bar.setContentDescription(label+", "+(progress+1)+UiText.get(R.string.ui_006));
                if(fromUser)scheduleSave();
            }
            public void onStartTrackingTouch(SeekBar bar){tracking=true;handler.removeCallbacks(persist);}
            public void onStopTrackingTouch(SeekBar bar){tracking=false;if(pending)scheduleSave();}
        });
        column.addView(slider,new LinearLayout.LayoutParams(-1,dp(36)));
        parent.addView(column,new LinearLayout.LayoutParams(-1,-2));
        return slider;
    }
    private void save(){
        long saverValue=(saver.getProgress()+1)*60000L,sleepValue=(sleep.getProgress()+1)*60000L;
        if(sleepValue<=saverValue){restoreSliders();message(UiText.get(R.string.ui_031));return;}
        if(!Settings.System.canWrite(this)||checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")!=PackageManager.PERMISSION_GRANTED){
            restoreSliders();message(UiText.get(R.string.ui_030));return;
        }
        long previousSaver=Settings.System.getLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,600000);
        long previousSleep=Settings.Secure.getLong(getContentResolver(),"sleep_timeout",1200000);
        try{
            if(!Settings.System.putLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,saverValue))throw new IllegalStateException(UiText.get(R.string.ui_114));
            if(!Settings.Secure.putLong(getContentResolver(),"sleep_timeout",sleepValue))throw new IllegalStateException(UiText.get(R.string.ui_122));
            if(Settings.System.getLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT)!=saverValue || Settings.Secure.getLong(getContentResolver(),"sleep_timeout")!=sleepValue)throw new IllegalStateException(UiText.get(R.string.ui_139));

        }catch(Exception error){
            boolean restored=false;
            try{
                boolean a=Settings.System.putLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,previousSaver);
                boolean b=Settings.Secure.putLong(getContentResolver(),"sleep_timeout",previousSleep);
                restored=a&&b&&Settings.System.getLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT)==previousSaver&&Settings.Secure.getLong(getContentResolver(),"sleep_timeout")==previousSleep;
            }catch(Exception ignored){}
            restoreSliders();
            message(UiText.get(R.string.ui_039)+(restored?UiText.get(R.string.ui_090):UiText.get(R.string.ui_096)));
        }
    }
    private void restoreSliders(){
        long saverMs=Settings.System.getLong(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,600000);
        long sleepMs=Settings.Secure.getLong(getContentResolver(),"sleep_timeout",1200000);
        saver.setProgress((int)Math.max(1,Math.min(1440,(saverMs+59999)/60000))-1);
        sleep.setProgress((int)Math.max(1,Math.min(1440,(sleepMs+59999)/60000))-1);
    }
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xE6FFFFFF);t.setGravity(Gravity.CENTER);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void message(String text){android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_LONG).show();}
}
