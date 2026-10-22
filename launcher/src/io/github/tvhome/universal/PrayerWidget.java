package io.github.tvhome.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.URL;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Small daily schedule; requests only while Home is visible. No location permission. */
final class PrayerWidget extends LinearLayout {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView[] names = new TextView[5];
    private final TextView[] times = new TextView[5];
    private final String[] keys = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private boolean active, loading;
    private long retryAt;
    private int generation;
    PrayerWidget(Activity activity) {
        super(activity);
        this.activity = activity;
        prefs = activity.getSharedPreferences("prayer_widget", 0);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, 0);
        LinearLayout row = new LinearLayout(activity);
        String[] prayerNames = {UiText.get(R.string.ui_110), UiText.get(R.string.ui_144), UiText.get(R.string.ui_019), UiText.get(R.string.ui_080), UiText.get(R.string.ui_049)};
        for (int i=0; i<5; i++) {
            LinearLayout column = new LinearLayout(activity); column.setOrientation(VERTICAL);
            column.setGravity(Gravity.END);
            TextView name=label(8); name.setText(prayerNames[i]); names[i]=name; name.setTextColor(0xBBFFFFFF);
            times[i]=label(12); times[i].setTypeface(null, Typeface.BOLD); times[i].setText("--:--");
            name.setShadowLayer(dp(3),0,dp(1),0xCC000000);
            times[i].setShadowLayer(dp(3),0,dp(1),0xCC000000);
            name.setGravity(Gravity.RIGHT);
            times[i].setGravity(Gravity.RIGHT);
            name.setTextAlignment(android.view.View.TEXT_ALIGNMENT_GRAVITY);
            times[i].setTextAlignment(android.view.View.TEXT_ALIGNMENT_GRAVITY);
            column.addView(name, new LayoutParams(-1, -2));
            column.addView(times[i], new LayoutParams(-1, -2));
            row.addView(column,new LayoutParams(0,-2,1));
        }
        LayoutParams rp=new LayoutParams(-1,-2); rp.topMargin=0; addView(row,rp);
        setContentDescription(UiText.get(R.string.ui_064));
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(int size) { TextView v=new TextView(activity); v.setTextSize(size); v.setTextColor(Color.WHITE); return v; }
    void resume() { active=true; update(); }
    void pause() { active=false; }
    private String today() {
        SimpleDateFormat f=new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(prefs.getString("timezone",TimeZone.getDefault().getID())));
        return f.format(new Date());
    }
    void update() {
        if (!active) return;
        String city=prefs.getString("city", "");
        if (city.isEmpty()) return;
        String date=today();
        try {
            if (date.equals(prefs.getString("date",""))) {
                show(new JSONObject(prefs.getString("timings","{}")),city,date); return;
            }
        } catch (Exception ignored) { }
        for (int i=0;i<5;i++) { times[i].setText("--:--"); names[i].setTextColor(0xBBFFFFFF); times[i].setTextColor(Color.WHITE); }
        if (android.os.SystemClock.elapsedRealtime()<retryAt) {
            setContentDescription(UiText.get(R.string.ui_063) + city + UiText.get(R.string.ui_011));
            return;
        }
        setContentDescription(UiText.get(R.string.ui_063) + city + " · " + date + UiText.get(R.string.ui_012));
        if (loading) return;
        loading=true; int requestGeneration=generation;
        String state=prefs.getString("state","");
        String country=prefs.getString("country",Locale.getDefault().getDisplayCountry(Locale.ENGLISH));
        new Thread(() -> {
            JSONObject data=null; HttpsURLConnection c=null;
            try {
                String url="https://api.aladhan.com/v1/timingsByCity/"+date+"?method=3&school=0&country="
                    +URLEncoder.encode(country,"UTF-8")+"&city="+URLEncoder.encode(city,"UTF-8")+"&state="+URLEncoder.encode(state,"UTF-8");
                c=(HttpsURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(false);
                if (c.getResponseCode()!=200) throw new Exception("HTTP");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()) {
                    byte[] b=new byte[4096]; int n;
                    while((n=in.read(b))!=-1) { if(out.size()+n>131072) throw new Exception("Size"); out.write(b,0,n); }
                }
                JSONObject result=new JSONObject(out.toString("UTF-8"));
                if(result.optInt("code")!=200) throw new Exception("API");
                data=result.getJSONObject("data");
                if(!date.equals(data.getJSONObject("date").getJSONObject("gregorian").getString("date"))) throw new Exception("Date");
                JSONObject t=data.getJSONObject("timings");
                for(String key:keys) if(!t.getString(key).matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new Exception("Time");
                JSONObject meta=data.getJSONObject("meta");
                double lat=meta.getDouble("latitude"), lon=meta.getDouble("longitude");
                if (!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat>90||lon < -180||lon>180) throw new Exception("Location");
                String zone=meta.getString("timezone");
                if (!java.util.Arrays.asList(TimeZone.getAvailableIDs()).contains(zone)) throw new Exception("Timezone");
            } catch(Exception ignored) { data=null; }
            finally { if(c!=null) c.disconnect(); }
            final JSONObject fetched=data;
            handler.post(() -> {
                loading=false;
                if(requestGeneration!=generation || activity.isDestroyed()) { if(active) update(); return; }
                if(fetched!=null) {
                    try {
                        prefs.edit().putString("date",date).putString("timings",fetched.getJSONObject("timings").toString())
                            .putString("timezone",fetched.getJSONObject("meta").getString("timezone")).apply();
                        if(active) show(fetched.getJSONObject("timings"),city,date);
                    } catch(Exception ignored) { }
                } else {
                    retryAt=android.os.SystemClock.elapsedRealtime()+15*60*1000;
                    if(active) setContentDescription(UiText.get(R.string.ui_063) + city + UiText.get(R.string.ui_011));
                }
            });
        },"prayer-times").start();
    }
    private void show(JSONObject t,String city,String date) throws Exception {
        setContentDescription(UiText.get(R.string.ui_063)+city+" · "+date);
        String[] schedule=new String[5];
        for(int i=0;i<5;i++) schedule[i]=t.getString(keys[i]);
        Calendar now=Calendar.getInstance(TimeZone.getTimeZone(prefs.getString("timezone",TimeZone.getDefault().getID())));
        int next=NextPrayer.index(schedule,now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE));
        for(int i=0;i<5;i++) {
            times[i].setText(schedule[i]);
            names[i].setTextColor(i==next ? 0xFFFFFFFF : 0xBBFFFFFF);
            times[i].setTextColor(i==next ? 0xFFFFFFFF : Color.WHITE);
            names[i].setTypeface(null,i==next ? Typeface.BOLD : Typeface.NORMAL);
            // Align visible glyph edges, compensating for different font side bearings.
            android.graphics.Rect nameBounds = new android.graphics.Rect();
            android.graphics.Rect timeBounds = new android.graphics.Rect();
            String nameText = names[i].getText().toString();
            names[i].getPaint().getTextBounds(nameText, 0, nameText.length(), nameBounds);
            times[i].getPaint().getTextBounds(schedule[i], 0, schedule[i].length(), timeBounds);
            float nameInset = names[i].getPaint().measureText(nameText) - nameBounds.right;
            float timeInset = times[i].getPaint().measureText(schedule[i]) - timeBounds.right;
            names[i].setTranslationX(nameInset - timeInset);
            times[i].setContentDescription(schedule[i]+(i==next ? UiText.get(R.string.ui_013) : ""));
        }
    }
    void settings() {
        LinearLayout fields=new LinearLayout(activity); fields.setOrientation(VERTICAL); fields.setPadding(dp(20),dp(8),dp(20),0);
        EditText city=new EditText(activity); city.setSingleLine(true); city.setHint(UiText.get(R.string.ui_075)); city.setText(prefs.getString("city","")); fields.addView(city);
        EditText state=new EditText(activity); state.setSingleLine(true); state.setHint(UiText.get(R.string.ui_103)); state.setText(prefs.getString("state","")); fields.addView(state);
        EditText country=new EditText(activity);country.setSingleLine(true);country.setHint(UiText.get(R.string.country));country.setText(prefs.getString("country",Locale.getDefault().getDisplayCountry(Locale.ENGLISH)));fields.addView(country);
        TextView info=label(12); info.setText(UiText.get(R.string.prayer_method)); fields.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(UiText.get(R.string.ui_079)).setView(fields)
            .setPositiveButton(UiText.get(R.string.ui_120),null).setNegativeButton(UiText.get(R.string.ui_026),null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name=city.getText().toString().trim(), province=state.getText().toString().trim();
            if(name.isEmpty() || name.length()>100) { city.setError(UiText.get(R.string.ui_057)); return; }
            if(province.length()>100) { state.setError(UiText.get(R.string.ui_058)); return; }
            String countryName=country.getText().toString().trim();
            if(countryName.isEmpty()||countryName.length()>100){country.setError(UiText.get(R.string.country));return;}
            generation++; retryAt=0;
            prefs.edit().putString("city",name).putString("state",province).putString("country",countryName).remove("date").remove("timings").remove("timezone").apply();
            dialog.dismiss(); update();
        }));
        dialog.show();
        GlassControls.dialog(dialog);
    }
}
