package io.github.tvhome.universal;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
public class QuickSettingsActivity extends Activity {
    private QuickSettingsPanel panel;
    @Override public void onCreate(Bundle state){super.onCreate(state);panel=new QuickSettingsPanel(this,this::finish);setContentView(panel.view());}
    @Override protected void onResume(){super.onResume();panel.start();}
    @Override protected void onPause(){panel.stop();super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);finish();}
}
