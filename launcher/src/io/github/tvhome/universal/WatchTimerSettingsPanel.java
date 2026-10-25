package io.github.tvhome.universal;
import android.app.Activity;
import android.os.*;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import android.view.View;
final class WatchTimerSettingsPanel {
    private final android.content.Context context;
    private final java.util.function.Consumer<Intent> openPermission;
    private LinearLayout panel;
    private Switch enabled;
    private TextView status;
    private boolean updating;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        updating=true;enabled.setChecked(WatchTimerController.active());updating=false;
        long seconds=WatchTimerController.remaining();
        status.setText(seconds>0?UiText.get(R.string.watch_remaining)+" "+String.format(UiText.locale(),"%02d:%02d",seconds/60,seconds%60):UiText.get(R.string.watch_off));
        handler.postDelayed(this,1000);
    }};
    WatchTimerSettingsPanel(android.content.Context context, java.util.function.Consumer<Intent> openPermission){
        this.context=context;this.openPermission=openPermission;
        panel=NavigationSound.panel(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(4),0,dp(6));
        enabled=new Switch(context);enabled.setText(UiText.get(R.string.watch_enabled));MenuIcon.attach(enabled,"watch_timer",19,0xFFFFFFFF);panel.addView(enabled);gap(panel,8);
        TextView label=new TextView(context);label.setText(UiText.get(R.string.watch_duration));label.setTextColor(0xFFFFFFFF);label.setTextSize(13);label.setGravity(android.view.Gravity.CENTER_VERTICAL);label.setPadding(dp(14),0,dp(14),0);panel.addView(label,new LinearLayout.LayoutParams(-1,dp(28)));
        SeekBar minutes=new SeekBar(context);minutes.setMax(239);minutes.setKeyProgressIncrement(1);minutes.setPadding(dp(14),0,dp(14),0);
        minutes.setProgress(WatchTimerController.duration()-1);
        label.setText(UiText.get(R.string.watch_duration)+": "+(minutes.getProgress()+1));
        minutes.setContentDescription(label.getText());
        panel.addView(minutes,new LinearLayout.LayoutParams(-1,dp(36)));
        minutes.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                int duration=value+1;label.setText(UiText.get(R.string.watch_duration)+": "+duration);bar.setContentDescription(label.getText());
                if(fromUser)WatchTimerController.duration(duration);
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        status=new TextView(context);status.setTextColor(0xCCFFFFFF);status.setTextSize(12);status.setGravity(android.view.Gravity.CENTER_VERTICAL);status.setPadding(dp(14),0,dp(14),0);gap(panel,8);panel.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));gap(panel,8);
        Button start=new Button(context);start.setText(UiText.get(R.string.watch_start));start.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));start.setSingleLine(true);MenuIcon.attach(start,"refresh",19,0xFFFFFFFF);panel.addView(start);
        start.setOnClickListener(v->{start(minutes.getProgress()+1);});
        enabled.setOnCheckedChangeListener((button,on)->{if(updating)return;if(on){start(minutes.getProgress()+1);}else WatchTimerController.cancel();});

    }
    private void start(int minutes){
        if(Build.VERSION.SDK_INT<28){
            Toast.makeText(context,UiText.get(R.string.watch_requires_android9),Toast.LENGTH_LONG).show();resetToggle();return;
        }
        if(!WatchTimerController.alarmPermission()){
            Toast.makeText(context,UiText.get(R.string.watch_alarm_permission),Toast.LENGTH_LONG).show();resetToggle();
            if(Build.VERSION.SDK_INT>=31)try{openPermission.accept(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+context.getPackageName())));}catch(RuntimeException ignored){}
            return;
        }

        if(!LauncherOverrideService.isConnected()){
            Toast.makeText(context,UiText.get(R.string.watch_accessibility),Toast.LENGTH_LONG).show();
            updating=true;enabled.setChecked(false);updating=false;
            try{openPermission.accept(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(RuntimeException ignored){}
            return;
        }
        if(!WatchTimerController.start(minutes))Toast.makeText(context,UiText.get(R.string.watch_failed),Toast.LENGTH_LONG).show();
        handler.removeCallbacks(refresh);refresh.run();
    }
    View view(){return panel;}
    View focus(){return enabled;}
    void resume(){handler.removeCallbacks(refresh);refresh.run();}
    void pause(){handler.removeCallbacks(refresh);}
    private void resetToggle(){updating=true;enabled.setChecked(WatchTimerController.active());updating=false;}
    private void gap(LinearLayout panel,int height){panel.addView(new android.widget.Space(context),new LinearLayout.LayoutParams(-1,dp(height)));}
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
}
