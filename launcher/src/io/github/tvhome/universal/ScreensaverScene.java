package io.github.tvhome.universal;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.Bitmap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/** Clock design adapted from CleanYourTV ClockDream (MIT; see assets/cleanyourtv-LICENSE.txt). */
final class ScreensaverScene {
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private FrameLayout root;
    private LinearLayout content, clockRow;
    private TextView clock, date, ampm, sourceCredit;
    private boolean active;
    private long lastClockMinute=Long.MIN_VALUE;
    private String clockLocale="",clockZone="";
    private boolean clock24;
    private ImageView wallpaper, incoming;
    private FrameLayout backgroundStage;
    private int wallpaperGeneration;
    private boolean wallpaperLoading;
    private final Runnable rotateBackground = () -> loadBackground();
    private long backgroundStarted, backgroundDuration;
    private float fromZoom, toZoom, fromX, toX, fromY, toY;
    private boolean backgroundDirection;
    private final Runnable animateBackground = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (root.getWidth()==0 || root.getHeight()==0) {handler.postDelayed(this,100);return;}
            long now=android.os.SystemClock.elapsedRealtime();
            if (backgroundStarted==0) nextBackgroundMotion(now);
            float progress=Math.min(1f,(now-backgroundStarted)/(float)backgroundDuration);
            backgroundStage.setScaleX(fromZoom+(toZoom-fromZoom)*progress);
            backgroundStage.setScaleY(fromZoom+(toZoom-fromZoom)*progress);
            backgroundStage.setTranslationX(fromX+(toX-fromX)*progress);
            backgroundStage.setTranslationY(fromY+(toY-fromY)*progress);
            if(progress>=1f)nextBackgroundMotion(now);
            handler.postDelayed(this,33);
        }
    };
    private void nextBackgroundMotion(long now) {
        fromZoom=backgroundStage.getScaleX();fromX=backgroundStage.getTranslationX();fromY=backgroundStage.getTranslationY();
        backgroundDirection=!backgroundDirection;
        toZoom=backgroundDirection ? 1.30f : 1.18f;
        float direction=backgroundDirection ? 1f : -1f;
        toX=direction*root.getWidth()*(0.045f+random.nextFloat()*0.025f);
        toY=-direction*root.getHeight()*(0.035f+random.nextFloat()*0.025f);
        backgroundDuration=18000+random.nextInt(8001);backgroundStarted=now;
    }
    private void loadBackground() {
        if (!active || wallpaperLoading) return;
        wallpaperLoading=true;
        int generation=wallpaperGeneration;
        new Thread(() -> {
            MovieWallpaperProvider.Result result=null;
            try { result=MovieWallpaperProvider.random(context.getApplicationContext()); }
            catch(RuntimeException error) { android.util.Log.w("TVHomeScreensaver","Wallpaper unavailable",error); }
            Bitmap loaded=result==null?null:result.bitmap;
            if(loaded==null){
                try{
                    String[] local=context.getAssets().list("wallpapers");
                    if(local!=null&&local.length>0)try(java.io.InputStream input=context.getAssets().open("wallpapers/"+local[random.nextInt(local.length)])){
                        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
                        loaded=android.graphics.BitmapFactory.decodeStream(input,null,options);
                    }
                }catch(java.io.IOException|RuntimeException ignored){}
            }
            final Bitmap bitmap=loaded;
            final String credit=result==null||WallpaperSources.selected(context).equals("tmdb")?"":result.title;
            handler.post(() -> {
                wallpaperLoading=false;
                if (!active || generation!=wallpaperGeneration) {
                    if(bitmap!=null)bitmap.recycle();
                    if(active)loadBackground();
                    return;
                }
                if(bitmap!=null) {
                    sourceCredit.setText(credit);sourceCredit.setVisibility(credit.isEmpty()?View.GONE:View.VISIBLE);
                    incoming.animate().cancel();
                    incoming.setImageBitmap(bitmap);incoming.setAlpha(0f);
                    incoming.animate().alpha(1f).setDuration(1600).withEndAction(() -> {
                        wallpaper.setImageBitmap(bitmap);incoming.setImageDrawable(null);incoming.setAlpha(0f);
                    }).start();
                }
                handler.removeCallbacks(rotateBackground);
                handler.postDelayed(rotateBackground,120000);
            });
        },"screensaver-wallpaper").start();
    }
    private final Runnable refresh=new Runnable(){public void run(){
        if(!active)return;
        Date now=new Date();Locale id=UiText.locale();
        boolean full=android.text.format.DateFormat.is24HourFormat(context);
        long minute=now.getTime()/60000;
        String locale=id.toLanguageTag(),zone=java.util.TimeZone.getDefault().getID();
        if(minute!=lastClockMinute||full!=clock24||!locale.equals(clockLocale)||!zone.equals(clockZone)){
            lastClockMinute=minute;clock24=full;clockLocale=locale;clockZone=zone;
            clock.setText(new SimpleDateFormat(full?"HH:mm":"h:mm",id).format(now));
            ampm.setText(full?"":new SimpleDateFormat("a",id).format(now));
            date.setText(new SimpleDateFormat("EEEE, d MMMM",id).format(now).toUpperCase(id));
            fitClock();
        }
        handler.postDelayed(this,1000);
    }};
    private final Runnable move=new Runnable(){public void run(){
        if(!active)return;
        int availableX=Math.max(0,root.getWidth()-content.getWidth()-dp(24));
        int availableY=Math.max(0,root.getHeight()-content.getHeight()-dp(24));
        float targetX=dp(12)+availableX*random.nextFloat();
        float targetY=dp(12)+availableY*random.nextFloat();
        long duration=12000+random.nextInt(8001);
        content.animate().x(targetX).y(targetY).setDuration(duration)
                .setInterpolator(new android.view.animation.LinearInterpolator()).start();
        handler.postDelayed(this,duration);
    }};
    ScreensaverScene(Context context){
        this.context=context;
        root=new FrameLayout(context);root.setBackgroundColor(0xFF000000);
        wallpaper=new ImageView(context);incoming=new ImageView(context);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);incoming.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundStage=new FrameLayout(context);
        backgroundStage.setScaleX(1.18f);backgroundStage.setScaleY(1.18f);
        backgroundStage.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        backgroundStage.addView(incoming,new FrameLayout.LayoutParams(-1,-1));incoming.setAlpha(0f);
        root.addView(backgroundStage,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(context);shade.setBackgroundColor(0x77000000);root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        sourceCredit=text(10,0xBBFFFFFF);sourceCredit.setSingleLine(false);sourceCredit.setMaxLines(2);sourceCredit.setVisibility(View.GONE);
        FrameLayout.LayoutParams creditPosition=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM|android.view.Gravity.LEFT);
        creditPosition.setMargins(dp(20),0,dp(20),dp(12));root.addView(sourceCredit,creditPosition);
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);content.setClipChildren(false);content.setClipToPadding(false);
        LinearLayout row=new LinearLayout(context);clockRow=row;row.setBaselineAligned(true);row.setClipChildren(false);row.setClipToPadding(false);
        clock=text(120,0xE6FFFFFF);clock.setTypeface(Typeface.create("sans-serif-thin",Typeface.NORMAL));clock.setLetterSpacing(0f);clock.setIncludeFontPadding(true);clock.setPadding(dp(18),0,dp(18),0);
        ampm=text(22,0x99FFFFFF);ampm.setLetterSpacing(0.08f);
        row.addView(clock,new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,-2);ap.leftMargin=dp(10);row.addView(ampm,ap);content.addView(row,new LinearLayout.LayoutParams(-2,-2));
        date=text(18,0x8CFFFFFF);date.setLetterSpacing(0.14f);date.setPadding(dp(4),0,dp(4),0);date.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams dt=new LinearLayout.LayoutParams(-2,-2);dt.gravity=android.view.Gravity.CENTER_HORIZONTAL;dt.topMargin=dp(4);content.addView(date,dt);
        content.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(active && (r-l!=or-ol || b-t!=ob-ot)){content.animate().cancel();place();handler.removeCallbacks(move);handler.post(move);}});root.addView(content,new FrameLayout.LayoutParams(-2,-2));
    }
    private int textWidth(TextView view) {
        return (int)Math.ceil(view.getPaint().measureText(view.getText().toString()))
                + view.getPaddingLeft() + view.getPaddingRight() + dp(8);
    }
    private void fitClock() {
        int clockWidth=textWidth(clock);
        int suffixWidth=ampm.getText().length()==0 ? 0 : textWidth(ampm);
        if(clock.getLayoutParams().width!=clockWidth) {
            LinearLayout.LayoutParams cp=(LinearLayout.LayoutParams)clock.getLayoutParams();
            cp.width=clockWidth;clock.setLayoutParams(cp);
        }
        ampm.setVisibility(suffixWidth==0 ? View.GONE : View.VISIBLE);
        int rowWidth=clockWidth+(suffixWidth==0?0:suffixWidth+dp(10));
        if(clockRow.getLayoutParams().width!=rowWidth) {
            LinearLayout.LayoutParams rp=(LinearLayout.LayoutParams)clockRow.getLayoutParams();
            rp.width=rowWidth;clockRow.setLayoutParams(rp);
        }
        int contentWidth=Math.max(rowWidth,textWidth(date));
        if(content.getLayoutParams().width!=contentWidth) {
            FrameLayout.LayoutParams fp=(FrameLayout.LayoutParams)content.getLayoutParams();
            fp.width=contentWidth;content.setLayoutParams(fp);
        }
    }
    private void place(){
        int w=Math.max(0,root.getWidth()-content.getWidth()),h=Math.max(0,root.getHeight()-content.getHeight());
        float x=w*(0.1f+0.8f*random.nextFloat()),y=h*(0.1f+0.8f*random.nextFloat());content.setX(x);content.setY(y);
    }
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView text(int size,int color){TextView t=new TextView(context);t.setTextSize(size);t.setTextColor(color);t.setSingleLine(true);return t;}
    private final Runnable initial=()->{if(active){place();content.setAlpha(1f);handler.removeCallbacks(move);handler.post(move);}};
    View view(){return root;}
    void start(){if(active)return;active=true;lastClockMinute=Long.MIN_VALUE;content.setAlpha(0f);handler.post(refresh);root.post(initial);backgroundStarted=0;handler.post(animateBackground);loadBackground();}
    void stop(){active=false;backgroundStage.animate().cancel();wallpaperGeneration++;incoming.animate().cancel();incoming.setImageDrawable(null);wallpaper.setImageDrawable(null);handler.removeCallbacksAndMessages(null);root.removeCallbacks(initial);content.animate().cancel();}
}
