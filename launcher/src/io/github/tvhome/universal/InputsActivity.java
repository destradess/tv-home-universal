package io.github.tvhome.universal;
import android.app.Activity;
import android.os.Bundle;
import android.media.tv.TvInputInfo;
import android.widget.*;
public class InputsActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout panel=NavigationSound.panel(this);panel.setOrientation(LinearLayout.VERTICAL);
        Button first=null;
        for(TvInputInfo input:TvPlatform.inputs(this)){
            Button row=button(String.valueOf(input.loadLabel(this)),input.getType()==TvInputInfo.TYPE_HDMI?"hdmi":"input",()->TvPlatform.openInput(this,input.getId()));
            panel.addView(row);if(first==null)first=row;
        }
        Button live=button(UiText.get(R.string.ui_077),"antenna",()->{if(!TvPlatform.openLiveTv(this))Toast.makeText(this,UiText.get(R.string.feature_unavailable),Toast.LENGTH_SHORT).show();});panel.addView(live);
        Button settings=button(UiText.get(R.string.input_settings),"settings",()->TvPlatform.openInputs(this));panel.addView(settings);
        SettingsPopup.show(this,UiText.get(R.string.ui_052),panel,first==null?settings:first);
    }
    private Button button(String text,String icon,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);MenuIcon.attach(b,icon,19,0xFFFFFFFF);b.setOnClickListener(v->{action.run();finish();});return b;}
}
