package io.github.tvhome.universal;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

final class SettingsPopup {
    static Intent position(Intent intent,View anchor){
        int[] xy=new int[2];anchor.getLocationOnScreen(xy);
        return intent.putExtra("popup_x",xy[0]).putExtra("popup_y",xy[1]+anchor.getHeight()+Math.round(8*anchor.getResources().getDisplayMetrics().density));
    }
    static PopupWindow at(Activity activity,View anchor,String title,View body,View focus,Runnable onDismiss){
        PopupWindow popup=create(activity,title,body,focus,onDismiss);
        popup.showAsDropDown(anchor,0,dp(activity,8),Gravity.START);
        NavigationSound.event(activity,NavigationSound.OPEN);
        if(focus!=null)focus.requestFocus();return popup;
    }
    static void show(Activity activity,String title,View body,View focus){
        FrameLayout host=new FrameLayout(activity);activity.setContentView(host);
        host.post(()->{
            if(activity.isFinishing())return;
            PopupWindow popup=create(activity,title,body,focus,activity::finish);
            int width=dp(activity,width(title));
            int x=activity.getIntent().getIntExtra("popup_x",host.getWidth()-width-dp(activity,48));
            int y=activity.getIntent().getIntExtra("popup_y",dp(activity,74));
            if(activity.getIntent().getBooleanExtra("popup_quick_position",false)){
                popup.showAtLocation(host,Gravity.TOP|Gravity.RIGHT,dp(activity,16),dp(activity,16));
            }else{
                popup.showAtLocation(host,Gravity.TOP|Gravity.LEFT,Math.max(0,Math.min(x,host.getWidth()-width)),y);
            }
            NavigationSound.event(activity,NavigationSound.OPEN);
            if(focus!=null)focus.requestFocus();
        });
    }
    private static PopupWindow create(Activity activity,String title,View body,View focus,Runnable onDismiss){
        GlassControls.apply(body);
        if(UiText.get(R.string.navigation_sound).equals(title)||UiText.get(R.string.wallpaper_categories).equals(title)||UiText.get(R.string.language_title).equals(title)||compact(title)||UiText.get(R.string.ui_113).equals(title)||UiText.get(R.string.ui_069).equals(title))GlassControls.focusOnlyRows(body);
        sizeRows(activity,body);
        LinearLayout shell=NavigationSound.panel(activity);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));
        shell.setBackground(GlassMenu.background(activity));
        NavigationSound.attach(shell);
        TextView caption=new TextView(activity);caption.setText(title);caption.setTextColor(0xBBFFFFFF);caption.setTextSize(11);caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setPadding(dp(activity,8),0,0,dp(activity,6));
        shell.addView(caption,new LinearLayout.LayoutParams(-1,dp(activity,28)));
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(true);scroll.setScrollbarFadingEnabled(false);
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        body.measure(View.MeasureSpec.makeMeasureSpec(dp(activity,width(title)-16),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(activity,240),Math.max(dp(activity,36),body.getMeasuredHeight()))));
        PopupWindow popup=new PopupWindow(shell,dp(activity,width(title)),-2,true);popup.setOutsideTouchable(true);popup.setElevation(dp(activity,16));popup.setOnDismissListener(()->{NavigationSound.event(activity,NavigationSound.CLOSE);onDismiss.run();});return popup;
    }
    private static void sizeRows(Activity activity,View view){
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)sizeRows(activity,group.getChildAt(i));
        }
        if(view instanceof android.widget.Button || view instanceof android.widget.Switch){
            TextView row=(TextView)view;
            row.setTextSize(13);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
            row.setPadding(dp(activity,"widget_suboption".equals(row.getTag())?28:14),0,dp(activity,14),0);
            row.setMinimumHeight(0);row.setMinHeight(0);
            android.view.ViewGroup.LayoutParams params=row.getLayoutParams();
            if(params!=null){
                params.height=dp(activity,36);
                if(params instanceof android.view.ViewGroup.MarginLayoutParams){
                    android.view.ViewGroup.MarginLayoutParams margins=(android.view.ViewGroup.MarginLayoutParams)params;
                    margins.setMargins(0,0,0,0);
                }
                row.setLayoutParams(params);
            }
        }
    }
    private static boolean compact(String title){return UiText.get(R.string.ui_091).equals(title)||UiText.get(R.string.ui_140).equals(title)||UiText.get(R.string.ui_143).equals(title);}
    private static int width(String title){return UiText.get(R.string.navigation_sound).equals(title)?280:UiText.get(R.string.wallpaper_categories).equals(title)?244:UiText.get(R.string.language_title).equals(title)?280:UiText.get(R.string.ui_143).equals(title)||UiText.get(R.string.ui_069).equals(title)||UiText.get(R.string.ui_091).equals(title)?244:220;}
    private static int dp(Activity activity,int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
