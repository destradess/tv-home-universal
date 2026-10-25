package io.github.tvhome.universal;
import android.app.Activity;
import android.os.Bundle;
public class WatchTimerSettingsActivity extends Activity {
    private WatchTimerSettingsPanel panel;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        panel=new WatchTimerSettingsPanel(this,this::startActivity);
        SettingsPopup.show(this,UiText.get(R.string.watch_title),panel.view(),panel.focus());
    }
    @Override protected void onResume(){super.onResume();panel.resume();}
    @Override protected void onPause(){panel.pause();super.onPause();}
}
