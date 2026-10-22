package io.github.tvhome.universal;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.graphics.Typeface;
import android.view.View;
public class SoundSettingsActivity extends Activity {
    private LinearLayout panel,children;
    private final Switch[] packs=new Switch[SoundPacks.IDS.length];
    private boolean updating;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        Switch toggle=row(UiText.get(R.string.navigation_sound),"sound_effects");
        toggle.setChecked(NavigationSound.enabled(this));panel.addView(toggle,new LinearLayout.LayoutParams(-1,dp(36)));
        children=new LinearLayout(this);children.setOrientation(LinearLayout.VERTICAL);
        panel.addView(children,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<packs.length;i++){
            final String id=SoundPacks.IDS[i];Switch option=row(SoundPacks.NAMES[i],"sound_effects");
            option.setTag("widget_suboption");option.setChecked(id.equals(NavigationSound.selected(this)));packs[i]=option;
            children.addView(option,new LinearLayout.LayoutParams(-1,dp(36)));
            option.setOnCheckedChangeListener((button,checked)->{
                if(updating)return;
                updating=true;
                if(checked){
                    NavigationSound.select(this,id);
                    for(int n=0;n<packs.length;n++)packs[n].setChecked(SoundPacks.IDS[n].equals(id));
                }else button.setChecked(id.equals(NavigationSound.selected(this)));
                updating=false;
            });
        }
        TextView label=new TextView(this);label.setTextSize(13);label.setTextColor(0xFFFFFFFF);
        label.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));label.setPadding(dp(14),dp(6),dp(14),0);
        MenuIcon.attach(label,"sound",19,0xFFFFFFFF);children.addView(label,new LinearLayout.LayoutParams(-1,dp(32)));
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setKeyProgressIncrement(5);
        volume.setPadding(dp(14),0,dp(14),0);volume.setProgress(NavigationSound.volume(this));
        children.addView(volume,new LinearLayout.LayoutParams(-1,dp(36)));
        label.setText(UiText.get(R.string.navigation_sound_volume)+" : "+volume.getProgress()+"%");volume.setContentDescription(label.getText());
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar slider,int value,boolean fromUser){
                label.setText(UiText.get(R.string.navigation_sound_volume)+" : "+value+"%");slider.setContentDescription(label.getText());
                if(fromUser){NavigationSound.setVolume(SoundSettingsActivity.this,value);NavigationSound.event(SoundSettingsActivity.this,NavigationSound.VOLUME);}
            }
            public void onStartTrackingTouch(SeekBar slider){}
            public void onStopTrackingTouch(SeekBar slider){}
        });
        children.setVisibility(toggle.isChecked()?View.VISIBLE:View.GONE);
        toggle.setOnCheckedChangeListener((button,enabled)->{
            NavigationSound.setEnabled(this,enabled);children.setVisibility(enabled?View.VISIBLE:View.GONE);resize();
            if(enabled)NavigationSound.event(this,NavigationSound.ON);
        });
        SettingsPopup.show(this,UiText.get(R.string.navigation_sound),panel,toggle);
    }
    private Switch row(String label,String icon){
        Switch row=new Switch(this);row.setText(label);row.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        MenuIcon.attach(row,icon,19,0xFFFFFFFF);return row;
    }
    private void resize(){
        if(!(panel.getParent() instanceof ScrollView))return;
        ScrollView scroll=(ScrollView)panel.getParent();
        panel.measure(View.MeasureSpec.makeMeasureSpec(scroll.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        android.view.ViewGroup.LayoutParams params=scroll.getLayoutParams();params.height=Math.min(dp(240),Math.max(dp(36),panel.getMeasuredHeight()));scroll.setLayoutParams(params);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
