package io.github.tvhome.universal;

import android.content.Context;
import android.view.KeyEvent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Translucent remote-controlled panel, independent of the Google TV launcher. */
final class QuickSettingsPanel {
    private final Context context;
    private final Runnable dismiss;
    private FrameLayout root;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView date, clock;
    private View first, timerTile, menuView, timerView;
    private WatchTimerSettingsPanel timerPanel;
    private final Runnable tick=new Runnable() {
        public void run() {
            Date now=new Date(); Locale id=UiText.locale();
            date.setText(new SimpleDateFormat("EEE, d MMM",id).format(now));
            clock.setText(android.text.format.DateFormat.getTimeFormat(context).format(now));
            handler.postDelayed(this,60000-System.currentTimeMillis()%60000);
        }
    };
    QuickSettingsPanel(Context context, Runnable dismiss) {
        this.context=context;
        this.dismiss=dismiss;
        root=new FrameLayout(context) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                NavigationSound.key(context,event,findFocus());
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
                    if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())back();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        NavigationSound.attach(root);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root.setOnClickListener(v -> back());
        ScrollView scroll=new ScrollView(context); scroll.setFillViewport(false); scroll.setClipToOutline(true);
        scroll.setBackground(GlassMenu.background(context)); scroll.setOnClickListener(v -> {});
        FrameLayout.LayoutParams panelParams=new FrameLayout.LayoutParams(dp(320),-2,Gravity.END|Gravity.TOP);
        panelParams.setMargins(dp(16),dp(16),dp(16),dp(16)); root.addView(scroll,panelParams);menuView=scroll;
        LinearLayout panel=new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(16),dp(16),dp(16),dp(16));
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));
        LinearLayout header=new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stamp=new LinearLayout(context); stamp.setOrientation(LinearLayout.VERTICAL);
        date=label(11); clock=label(24); clock.setTypeface(null,Typeface.BOLD); stamp.addView(date); stamp.addView(clock);
        header.addView(stamp,new LinearLayout.LayoutParams(0,-2,1));
        TextView all=tile(UiText.get(R.string.ui_117), () -> launch(new Intent(Settings.ACTION_SETTINGS)));
        MenuIcon.attach(all,"settings",18,Color.WHITE);
        all.setTextSize(11); all.setPadding(dp(10),dp(8),dp(10),dp(8));
        header.addView(all,new LinearLayout.LayoutParams(-2,dp(40))); panel.addView(header);
        String[] names={UiText.get(R.string.ui_112),UiText.get(R.string.watch_title),UiText.get(R.string.ui_051),UiText.get(R.string.ui_130),UiText.get(R.string.ui_034),"Wi-Fi","Bluetooth",UiText.get(R.string.language_title)};
        String[] icons={"screensaver","watch_timer","input","display","sound","wifi","bluetooth","language"};
        int[] targets={0,-1,1,2,3,4,5,-2};
        for(int row=0;row<4;row++) {
            LinearLayout line=new LinearLayout(context);
            for(int col=0;col<2;col++) {
                final int index=row*2+col;
                TextView tile=tile(names[index], () -> {
                    if(targets[index]==-1)showTimer();
                    else if(targets[index]==-2)launch(new Intent(context,LanguageSettingsActivity.class));
                    else open(targets[index]);
                });
                MenuIcon.attach(tile,icons[index],19,Color.WHITE);
                if(targets[index]==-1){timerTile=tile;tile.setTextSize(12);tile.setMaxLines(2);}
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1);
                lp.setMargins(col==0?0:dp(5),row==0?dp(16):dp(8),col==0?dp(5):0,0);
                line.addView(tile,lp); if(index==0) first=tile;
            }
            panel.addView(line,new LinearLayout.LayoutParams(-1,-2));
        }
        TextView footer=label(10); footer.setText("TV Home"); footer.setTextColor(0x99FFFFFF); footer.setPadding(0,dp(12),0,0); panel.addView(footer);

    }
    private void showTimer(){
        if(timerPanel!=null)return;
        handler.removeCallbacks(tick);
        timerPanel=new WatchTimerSettingsPanel(context,intent->launch(intent));
        timerView=SettingsPopup.content(context,UiText.get(R.string.watch_title),timerPanel.view());
        timerView.setOnClickListener(v->{});
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(244),-2,Gravity.END|Gravity.TOP);
        params.setMargins(dp(16),dp(16),dp(16),dp(16));
        menuView.setVisibility(View.GONE);
        root.addView(timerView,params);
        timerPanel.resume();timerPanel.focus().requestFocus();
        NavigationSound.event(context,NavigationSound.OPEN);
    }
    private void back(){
        if(timerPanel==null){dismiss.run();return;}
        timerPanel.pause();timerPanel=null;
        root.removeView(timerView);timerView=null;
        menuView.setVisibility(View.VISIBLE);timerTile.requestFocus();
        handler.removeCallbacks(tick);handler.post(tick);
        NavigationSound.event(context,NavigationSound.CLOSE);
    }
    private void open(int index) {
        boolean opened=false;
        switch(index){
            case 0:launch(new Intent(context,QuickScreensaverActivity.class));return;
            case 1:launch(new Intent(context,InputsActivity.class));return;
            case 2:opened=TvPlatform.openDisplay(context);break;
            case 3:opened=TvPlatform.openSound(context);break;
            case 4:opened=TvPlatform.launch(context,TvPlatform.settings(Settings.ACTION_WIFI_SETTINGS));break;
            case 5:opened=TvPlatform.openBluetooth(context);break;
        }
        if(opened)dismiss.run();else Toast.makeText(context,UiText.get(R.string.feature_unavailable),Toast.LENGTH_SHORT).show();
    }
    private void launch(Intent... choices) {
        for(Intent intent:choices) try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); dismiss.run(); return; } catch(RuntimeException ignored) { }
        Toast.makeText(context,UiText.get(R.string.ui_088),Toast.LENGTH_LONG).show();
    }
    private TextView tile(String title,Runnable action) {
        TextView text=label(13); text.setText(title); text.setGravity(Gravity.CENTER_VERTICAL); text.setPadding(dp(12),0,dp(8),0);
        text.setFocusable(true); text.setClickable(true); text.setId(View.generateViewId());
        DockGlassDrawable glass=new DockGlassDrawable(context.getResources().getDisplayMetrics().density);
        text.setBackground(glass);
        text.setOnFocusChangeListener((v,focused) -> {glass.setFocused(focused);text.setTextColor(Color.WHITE);MenuIcon.color(text,Color.WHITE);});
        text.setOnClickListener(v -> action.run()); return text;
    }
    private TextView label(int size) {TextView v=new TextView(context);v.setTextColor(Color.WHITE);v.setTextSize(size);return v;}
    private int dp(int n) {return Math.round(n*context.getResources().getDisplayMetrics().density);}
    View view(){return root;}
    void start(){NavigationSound.event(context,NavigationSound.SIDE_OPEN);handler.removeCallbacks(tick);if(timerPanel!=null){timerPanel.resume();timerPanel.focus().requestFocus();}else{handler.post(tick);first.requestFocus();}}
    void stop(){NavigationSound.event(context,NavigationSound.SIDE_CLOSE);handler.removeCallbacksAndMessages(null);if(timerPanel!=null)timerPanel.pause();}
}
