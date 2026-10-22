package io.github.tvhome.universal;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Apple TV style home screen: clock top-left, pills top-right, the focused dock app's name over an
 * wallpaper, a dock of pinned apps, and every other app in a grid that scrolls up on Down.
 * Plain framework views only (no AndroidX), no network requests.
 * Optional launcher override is provided by a user-enabled accessibility service.
 */
public class MainActivity extends Activity {

    // Dock, hidden apps, Live TV and grid width come from res/values/config.xml.
    private String[] dockApps;
    private final List<App> orderedApps = new ArrayList<>();
    private String focusPackage;
    private String movingPackage;
    private List<String> movePreview;
    private boolean moveWaitRelease;
    private long lastMoveAt;
    private final Set<String> hidden = new HashSet<>();
    private String liveTvPkg, inputPickerPkg;
    private int cols;

    static final class App {
        String pkg, label;
        ComponentName component;
        Drawable art;
        boolean isBanner;
        int color;
        boolean googleTvShortcut;
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private FrameLayout hero;
    private LinearLayout grid;
    private ImageView wallBack, wallFront;
    private View gridShade;
    private String[] wallpapers = new String[0];
    private int wallIndex = -1;
    private long wallChangedAt;
    private boolean wallpaperLoaded;
    private int wallpaperRequest;
    private long wallpaperConfigRevision = -1;
    private TextView wallpaperCaption;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateWallpaper = this::nextWallpaper;
    static final long WALLPAPER_INTERVAL_MS = 2 * 60 * 1000;
    private View orderAnchor;
    private PopupWindow orderPopup;
    private PrayerWidget prayerWidget;
    private LinearLayout clockWidget, clockLabels;
    private TextView greeting, clock, ampm, date, dateSeparator, title, wifiPill, appsLabel;
    private int lastFocusedId = View.NO_ID;
    private boolean firstShow = true;
    private android.animation.ValueAnimator appLaunchBounce;
    private View appLaunchCard;
    private float appLaunchRestScale;
    private boolean appLaunchDispatched;
    private int packageSeq;
    private boolean appCatalogDirty=true;
    private String appCatalogLocale="";
    private Map<String,App> appCatalog;
    private List<App> sortedCatalog;
    private final Map<String,String> catalogUpdates=new HashMap<>();
    private List<String> renderedAppState;
    private PopupWindow inputsPopup;
    private ConnectivityManager connectivity;
    private boolean networkCallbackRegistered;
    private String networkPopupLabel="Offline";
    private final ConnectivityManager.NetworkCallback networkCallback=TvPlatform.networkCallback(new TvPlatform.NetworkListener(){
        public void available(Network network){updateWifi();}
        public void capabilities(Network network,NetworkCapabilities caps){showNetworkStatus(caps);}
        public void lost(Network network){updateWifi();}
    });

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(i.getAction()))updateWifi();
            else updateClock();
        }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            appCatalogDirty=true;
            colorCache.clear();
            buildApps();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dockApps = getResources().getStringArray(R.array.dock_apps);
        hidden.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        liveTvPkg = getString(R.string.live_tv_package);
        inputPickerPkg = getString(R.string.input_picker_package);
        cols = getResources().getInteger(R.integer.columns);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        buildApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        TvPlatform.register(this,packages,pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cancelAppLaunchBounce();
        LauncherOverrideService.POLICY.returnToTvHome();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        TvPlatform.register(this,tick,tf);
        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivity != null && !networkCallbackRegistered) {
            try {
                connectivity.registerDefaultNetworkCallback(networkCallback, handler);
                networkCallbackRegistered = true;
            } catch (RuntimeException ignored) { }
        }
        applyWidgetVisibility();
        updateClock();
        updateWifi();
        // Refresh metadata, but retain the card views and their focus when nothing changed.
        buildApps();
        if (getCurrentFocus() == null) {
            View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
            if (last == null && !dockCards.isEmpty()) last = dockCards.get(0);
            if (last != null && !last.hasFocus()) last.requestFocus();
        }
        if (firstShow) { firstShow = false; playEntrance(); }
        // New photo on return if the current one has been up for the full interval.
        long revision = MovieWallpaperProvider.prefs(this).getLong("revision", 0);
        boolean sourceChanged = revision != wallpaperConfigRevision;
        wallpaperConfigRevision = revision;
        if (sourceChanged || !wallpaperLoaded || wallIndex < 0 || SystemClock.elapsedRealtime() - wallChangedAt >= WALLPAPER_INTERVAL_MS) nextWallpaper();
        else scheduleWallpaper(WALLPAPER_INTERVAL_MS - (SystemClock.elapsedRealtime() - wallChangedAt));
    }

    @Override
    protected void onPause() {
        if(movingPackage!=null)finishDirectMove(false,false);
        if (!appLaunchDispatched) cancelAppLaunchBounce();
        super.onPause();
        prayerWidget.pause();
        handler.removeCallbacks(rotateWallpaper);
        unregisterReceiver(tick);
        if (networkCallbackRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkCallbackRegistered = false;
        }
        if (inputsPopup != null) inputsPopup.dismiss();
        if (orderPopup != null) orderPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        cancelAppLaunchBounce();
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(movingPackage!=null)finishDirectMove(false,false);
        // Home pressed while already home: jump back to the first dock app.
        if (inputsPopup != null) inputsPopup.dismiss();
        if (orderPopup != null) orderPopup.dismiss();
        if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; Back from the grid returns to the dock.
        if (scroll.getScrollY() > 0 && !dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Wallpaper: two stacked layers so a new photo can crossfade in over the old one.
        wallBack = wallpaperLayer();
        wallFront = wallpaperLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));
        // Scrim: darker at the top (clock, pills) and bottom (dock) so text stays readable.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x40000000, 0x26000000, 0x8C000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0x66000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));
        try {
            wallpapers = getAssets().list("wallpapers");
        } catch (java.io.IOException ignored) { }


        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        // Hero: exactly one screen tall, header on top, title + dock at the bottom.
        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        content.addView(hero, new LinearLayout.LayoutParams(-1, screenH));

        hero.addView(buildHeader(), lp(-1, -2, Gravity.TOP, dp(48), dp(36), dp(48), 0));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        hero.addView(bottom, lp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(30)));

        wallpaperCaption = text(12, "sans-serif-medium", 0xCCFFFFFF);
        wallpaperCaption.setSingleLine(true);
        wallpaperCaption.setVisibility(View.GONE);
        bottom.addView(wallpaperCaption, llp(-1, -2, dp(8), 0, 0, dp(8)));
        title = text(34, "sans-serif-light", Color.WHITE);
        title.setShadowLayer(dp(18), 0, 0, 0x66000000);
        title.setSingleLine(true);
        bottom.addView(title, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(dp(8), 0, 0, dp(14));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(20), dp(22), dp(20), dp(22));
        dockRow.setBackground(new DockGlassDrawable(getResources().getDisplayMetrics().density));
        bottom.addView(dockRow, new LinearLayout.LayoutParams(-1, -2));

        appsLabel = text(11, "sans-serif-medium", 0x99FFFFFF);
        appsLabel.setText(UiText.get(R.string.ui_018));
        appsLabel.setLetterSpacing(0.22f);
        content.addView(appsLabel, llp(-2, -2, dp(68), dp(8), 0, dp(18)));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(68), 0, dp(68), dp(160)));
        return root;
    }

    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greeting = text(10, "sans-serif-medium", Color.WHITE);
        greeting.setLetterSpacing(0.25f);
        greeting.setSingleLine(true);
        greeting.setPadding(0, 0, dp(16), 0);
        greeting.setIncludeFontPadding(false);

        LinearLayout clockRow = new LinearLayout(this);
        clockWidget = clockRow;
        clockRow.setGravity(Gravity.BOTTOM);
        clock = text(54, "sans-serif", Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        clockRow.addView(clock);
        ampm = text(12, "sans-serif-light", 0xCCFFFFFF);
        clockRow.addView(ampm, llp(-2, -2, dp(6), 0, 0, dp(8)));
        LinearLayout timeAndDate = new LinearLayout(this);
        timeAndDate.setOrientation(LinearLayout.HORIZONTAL);
        timeAndDate.setGravity(Gravity.BOTTOM);
        timeAndDate.setBaselineAligned(false);
        timeAndDate.addView(clockRow);
        dateSeparator = text(26, "sans-serif-thin", Color.WHITE);
        dateSeparator.setText("");
        dateSeparator.setBackgroundColor(Color.WHITE);
        dateSeparator.setIncludeFontPadding(false);
        timeAndDate.addView(dateSeparator, llp(dp(1), dp(40), dp(2), 0, dp(6), 0));
        left.addView(timeAndDate, llp(-2, -2, 0, dp(2), 0, dp(2)));

        date = text(10, "sans-serif-medium", Color.WHITE);
        date.setSingleLine(true);
        date.setIncludeFontPadding(false);
        date.setPadding(0, 0, dp(16), 0); // letterSpacing is not counted in the measured width
        date.setLetterSpacing(0.22f);
        clockLabels = new LinearLayout(this);
        clockLabels.setOrientation(LinearLayout.VERTICAL);
        clockLabels.setClipChildren(false);
        clockLabels.setClipToPadding(false);
        clockLabels.addView(greeting, new LinearLayout.LayoutParams(-2, -2));
        clockLabels.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        clockLabels.addView(date, new LinearLayout.LayoutParams(-2, -2));
        timeAndDate.addView(clockLabels, new LinearLayout.LayoutParams(-2, -2));
        clockLabels.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> alignClockLabels());
        timeAndDate.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> alignClockLabels());
        for (TextView t : new TextView[]{greeting, clock, ampm, date, dateSeparator}) t.setShadowLayer(dp(10), 0, dp(1), 0xB3000000);
        header.addView(left, new FrameLayout.LayoutParams(dp(420), -2, Gravity.START | Gravity.TOP));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        wifiPill = pill(UiText.get(R.string.ui_068), "wifi");
        wifiPill.setSingleLine(true);
        wifiPill.setMaxWidth(dp(250));
        wifiPill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wifiPill.setOnClickListener(v -> showNetworkMenu());
        TextView settings = pill(UiText.get(R.string.ui_118), "settings");
        settings.setOnClickListener(v -> showCompactMenu(settings, UiText.get(R.string.ui_118),
                new String[]{UiText.get(R.string.ui_117), UiText.get(R.string.ui_052), UiText.get(R.string.ui_091), UiText.get(R.string.ui_140), UiText.get(R.string.ui_143), UiText.get(R.string.ui_104), UiText.get(R.string.ui_113), UiText.get(R.string.ui_137), UiText.get(R.string.navigation_sound), UiText.get(R.string.ui_119)}, which -> {
                    if (which == 0) launch(new Intent(Settings.ACTION_SETTINGS));
                    else if (which == 1) showInputs(settings);
                    else if (which == 2) startActivity(SettingsPopup.position(new Intent(this, OverrideSettingsActivity.class), settings));
                    else if (which == 3) startActivity(SettingsPopup.position(new Intent(this, WallpaperSettingsActivity.class), settings));
                    else if (which == 4) showWidgetSettings();
                    else if (which == 5) startActivity(new Intent(this, QuickSettingsActivity.class));
                    else if (which == 6) startActivity(SettingsPopup.position(new Intent(this, ScreensaverSettingsActivity.class), settings));
                    else if (which == 7) showAppOrder();
                    else if (which == 8) startActivity(SettingsPopup.position(new Intent(this, SoundSettingsActivity.class), settings));
                    else showShutdown();
                }, "settings"));
        orderAnchor = settings;
        for (TextView p : new TextView[]{wifiPill, settings}) {
            right.addView(p, llp(-2, dp(30), dp(10), 0, 0, 0));
            pills.add(p);
        }
        header.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        prayerWidget = new PrayerWidget(this);
        FrameLayout.LayoutParams prayerLayout = new FrameLayout.LayoutParams(dp(320), -2, Gravity.END | Gravity.TOP);
        prayerLayout.topMargin = dp(40);
        header.addView(prayerWidget, prayerLayout);
        Runnable alignPrayer = () -> {
            if (settings.getWidth() == 0 || header.getWidth() == 0) return;
            android.graphics.Rect bounds = new android.graphics.Rect();
            settings.getDrawingRect(bounds);
            header.offsetDescendantRectToMyCoords(settings, bounds);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) prayerWidget.getLayoutParams();
            int end = header.getWidth() - bounds.right;
            int top = bounds.bottom + dp(10);
            if (params.rightMargin != end || params.topMargin != top) {
                params.rightMargin = end;
                params.topMargin = top;
                prayerWidget.setLayoutParams(params);
            }
        };
        header.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> alignPrayer.run());
        settings.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> header.post(alignPrayer));
        return header;
    }

    private TextView pill(String label, String glyph) {
        TextView p = text(11, "sans-serif-medium", 0xE6FFFFFF);
        p.setText(label);
        MenuIcon.attach(p, glyph, 16, 0xE6FFFFFF);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(14), 0, dp(14), 0);
        p.setFocusable(true);
        p.setId(View.generateViewId());
        DockGlassDrawable bg = new DockGlassDrawable(getResources().getDisplayMetrics().density);
        p.setBackground(bg);
        p.setOnFocusChangeListener((v, has) -> {
            bg.setFocused(has);
            ((TextView) v).setTextColor(has ? 0xFFFFFFFF : 0xE6FFFFFF);
            MenuIcon.color((TextView) v, has ? 0xFFFFFFFF : 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(220).start();
            if (has) { lastFocusedId = v.getId(); scroll.smoothScrollTo(0, 0); updateBackdropShade(1f); }
        });
        return p;
    }

    // ---------------------------------------------------------------- apps

    private void refreshAppCatalog() {
        PackageManager pm=getPackageManager();
        String locale=UiText.locale().toLanguageTag();
        if(appCatalog!=null&&!appCatalogDirty&&locale.equals(appCatalogLocale)){
            // Reordering uses the existing catalog; package broadcasts invalidate it separately.
            if(movingPackage!=null)return;
            android.content.pm.ChangedPackages changed=pm.getChangedPackages(packageSeq);
            if(changed==null)return;
            packageSeq=changed.getSequenceNumber();
            if(changed.getPackageNames().isEmpty())return;
            colorCache.clear();
        }else{
            android.content.pm.ChangedPackages changed=pm.getChangedPackages(packageSeq);
            if(changed!=null)packageSeq=changed.getSequenceNumber();
        }
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, App> byPkg = new HashMap<>();
        List<ResolveInfo> activities = new ArrayList<>(pm.queryIntentActivities(q, 0));
        activities.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : activities) {
            ActivityInfo ai = ri.activityInfo;
            if (hidden.contains(ai.packageName) || byPkg.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.component = new ComponentName(ai.packageName, ai.name);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            a.isBanner = banner != null;
            a.art = banner != null ? banner : ri.loadIcon(pm);
            a.color = sampleColor(a.pkg, a.art);
            byPkg.put(a.pkg, a);
        }
        ActivityInfo nativeHome=TvPlatform.systemHome(this);
        if(nativeHome!=null){
            App system=new App();system.pkg=nativeHome.packageName;
            system.label=UiText.get(R.string.system_home);system.component=new ComponentName(nativeHome.packageName,nativeHome.name);
            system.googleTvShortcut=true;system.isBanner=true;system.art=getDrawable(R.drawable.google_tv_shortcut);
            system.color=sampleColor(system.pkg,system.art);byPkg.put(system.pkg,system);
            LauncherOverrideService.POLICY.configure(system.pkg,nativeHome.name);
        }
        List<App> alphabetical = new ArrayList<>(byPkg.values());
        Collections.sort(alphabetical, (x, y) -> {
            int labelOrder = x.label.compareToIgnoreCase(y.label);
            return labelOrder != 0 ? labelOrder : x.pkg.compareTo(y.pkg);
        });
        catalogUpdates.clear();
        for(App app:alphabetical){
            try{catalogUpdates.put(app.pkg,Long.toString(pm.getPackageInfo(app.pkg,0).lastUpdateTime));}
            catch(PackageManager.NameNotFoundException ignored){catalogUpdates.put(app.pkg,"missing");}
        }
        appCatalog=byPkg;sortedCatalog=alphabetical;
        appCatalogLocale=locale;appCatalogDirty=false;
    }

    private void buildApps() {
        refreshAppCatalog();
        Map<String,App> byPkg=appCatalog;
        List<App> alphabetical=sortedCatalog;
        List<String> installed = new ArrayList<>();
        for (App app : alphabetical) installed.add(app.pkg);
        String saved = movePreview!=null?String.join("\n",movePreview):getPreferences(MODE_PRIVATE).getString("app_order_v1", "");
        List<String> order = AppOrder.merge(Arrays.asList(saved.split("\n")),
                Arrays.asList(dockApps), installed);
        String mergedOrder=String.join("\n",order);
        if(movePreview==null&&!saved.equals(mergedOrder))getPreferences(MODE_PRIVATE).edit().putString("app_order_v1",mergedOrder).apply();
        orderedApps.clear();
        for (String pkg : order) orderedApps.add(byPkg.get(pkg));
        int dockSize = Math.min(cols, orderedApps.size());
        List<App> dock = new ArrayList<>(orderedApps.subList(0, dockSize));
        List<App> rest = new ArrayList<>(orderedApps.subList(dockSize, orderedApps.size()));

        // Package broadcasts and wake/resume can repeat without changing the UI.
        // Include identity, order, locale, label and update timestamp so real changes still render.
        List<String> state = new ArrayList<>();
        state.add(movingPackage==null?"":movingPackage);
        state.add(UiText.locale().toLanguageTag());
        state.add(Integer.toString(cols));
        for (App app : orderedApps) {
            state.add(app.pkg);state.add(app.component.flattenToString());state.add(app.label);
            state.add(Boolean.toString(app.isBanner));state.add(Boolean.toString(app.googleTvShortcut));
            state.add(catalogUpdates.get(app.pkg));
        }
        if (state.equals(renderedAppState)) return;
        renderedAppState = state;

        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i == 0 ? 0 : dp(10), 0, i == dock.size() - 1 ? 0 : dp(10), 0);
            dockRow.addView(tile, p);
        }

        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, llp(-1, -2, 0, 0, 0, dp(26)));
            }
            View tile = tile(rest.get(i), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % cols == 0 ? 0 : dp(10), 0, i % cols == cols - 1 ? 0 : dp(10), 0);
            row.addView(tile, p);
        }
        // Pad the last row so tiles keep their width.
        if (row != null) for (int i = rest.size() % cols; i != 0 && i < cols; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(10), 0, i == cols - 1 ? 0 : dp(10), 0);
            row.addView(new View(this), p);
        }
        appsLabel.setVisibility(rest.isEmpty() ? View.GONE : View.VISIBLE);
        wireFocus();

        if (!dock.isEmpty()) { title.setText(dock.get(0).label); }
        View last = null;
        if (focusPackage != null) {
            List<View> tiles = new ArrayList<>(dockCards);
            tiles.addAll(gridCards);
            for (View tile : tiles) if (((App) tile.getTag()).pkg.equals(focusPackage)) { last = tile; break; }
        }
        if (last == null) last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last != null) last.requestFocus();
        else if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    private void showOrderList(String heading, String[] labels, java.util.function.IntConsumer action) {
        showCompactMenu(orderAnchor, heading, labels, action, "order");
    }

    private void showCompactMenu(View anchor, String heading, String[] labels,
            java.util.function.IntConsumer action, String icon) {
        if (labels.length == 0 || anchor == null) return;
        if (orderPopup != null) orderPopup.dismiss();
        LinearLayout panel = NavigationSound.panel(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(GlassMenu.background(this));
        TextView caption = text(11, "sans-serif-medium", 0xBBFFFFFF);
        caption.setText(heading);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setPadding(dp(10), 0, dp(10), dp(6));
        panel.addView(caption, new LinearLayout.LayoutParams(-1, dp(28)));
        android.widget.ScrollView scroller = new android.widget.ScrollView(this);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setScrollbarFadingEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list, new android.widget.ScrollView.LayoutParams(-1, -2));
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, dp(36 * Math.min(6, labels.length))));
        NavigationSound.attach(panel);
        PopupWindow popup = new PopupWindow(panel, dp(220), -2, true);
        List<TextView> rows = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            String rowIcon = icon;
            if ("settings".equals(icon)) {
                String[] settingsIcons = {"settings", "input", "home", "wallpaper", "widgets", "quick", "sleep", "order", "sound", "power"};
                if (i < settingsIcons.length) rowIcon = settingsIcons[i];
            }
            if ("network".equals(icon)) {
                String[] networkIcons = {"wifi", "settings", "refresh"};
                rowIcon = i < networkIcons.length ? networkIcons[i] : "wifi";
            }
            TextView row = inputRow(labels[i], rowIcon, () -> {});
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setOnClickListener(v -> { popup.dismiss(); action.accept(index); });
            if("network".equals(icon)&&index==0){
                row.setOnLongClickListener(v->{
                    popup.dismiss();
                    startActivity(SettingsPopup.position(new Intent(this,WifiNetworksActivity.class),anchor));
                    return true;
                });
            }
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(36)));
            rows.add(row);
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView row = rows.get(i);
            row.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            row.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            row.setNextFocusLeftId(row.getId()); row.setNextFocusRightId(row.getId());
        }
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(16));
        popup.setOnDismissListener(() -> {
            NavigationSound.event(this,NavigationSound.CLOSE);
            if (orderPopup == popup) orderPopup = null;
            anchor.requestFocus();
        });
        orderPopup = popup;
        popup.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        NavigationSound.event(this,NavigationSound.OPEN);
        rows.get(0).requestFocus();
    }

    private void showNetworkMenu() {
        updateWifi();
        String address = UiText.get(R.string.ui_132);
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            android.net.LinkProperties properties = network == null ? null : manager.getLinkProperties(network);
            if (properties != null) {
                for (android.net.LinkAddress link : properties.getLinkAddresses()) {
                    if (link.getAddress() instanceof java.net.Inet4Address && !link.getAddress().isLoopbackAddress()) {
                        address = link.getAddress().getHostAddress(); break;
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        final String details = networkPopupLabel + " · IP: " + address;
        showCompactMenu(wifiPill, UiText.get(R.string.ui_068), new String[]{
                networkPopupLabel,
                UiText.get(R.string.ui_093), UiText.get(R.string.ui_105)
        }, which -> {
            if (which == 0) {
                android.widget.Toast.makeText(this, details, android.widget.Toast.LENGTH_LONG).show();
                showNetworkMenu();
            } else if (which == 1) {
                if (!launch(new Intent(Settings.ACTION_WIFI_SETTINGS))
                        && !launch(new Intent(Settings.ACTION_WIRELESS_SETTINGS))) launch(new Intent(Settings.ACTION_SETTINGS));
            } else showNetworkMenu();
        }, "network");
    }

    private void showAppOrder() {
        final List<App> snapshot = new ArrayList<>(orderedApps);
        String[] labels = new String[snapshot.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ". " + snapshot.get(i).label;
        showOrderList(UiText.get(R.string.ui_138), labels, which -> showMoveOptions(snapshot.get(which).pkg));
    }

    private void showMoveOptions(String pkg) {
        App selected = null;
        for (App app : orderedApps) if (app.pkg.equals(pkg)) { selected = app; break; }
        if (selected == null) return;
        final String label = selected.label;
        showOrderList(label, new String[]{UiText.get(R.string.ui_100), UiText.get(R.string.ui_065)}, which -> {
                    if (which == 1) { moveApp(pkg, 0); return; }
                    String[] positions = new String[orderedApps.size()];
                    for (int i = 0; i < positions.length; i++) positions[i] = UiText.get(R.string.ui_102) + (i + 1)
                            + (i < cols ? UiText.get(R.string.ui_009) : UiText.get(R.string.ui_010));
                    showOrderList(UiText.get(R.string.ui_099), positions, position -> moveApp(pkg, position));
                });
    }

    private void moveApp(String pkg, int position) {
        List<String> order = new ArrayList<>();
        for (App app : orderedApps) order.add(app.pkg);
        AppOrder.move(order, pkg, position);
        getPreferences(MODE_PRIVATE).edit().putString("app_order_v1", String.join("\n", order)).apply();
        focusPackage = pkg;
        buildApps();
    }

    private void startDirectMove(String pkg){
        if(movingPackage!=null||appLaunchCard!=null)return;
        movingPackage=pkg;movePreview=new ArrayList<>();
        for(App app:orderedApps)movePreview.add(app.pkg);
        focusPackage=pkg;moveWaitRelease=true;lastMoveAt=0;
        NavigationSound.event(this,NavigationSound.CONFIRM);
        buildApps();
        android.widget.Toast.makeText(this,UiText.get(R.string.app_move_hint),android.widget.Toast.LENGTH_LONG).show();
    }
    private void finishDirectMove(boolean save,boolean feedback){
        if(movingPackage==null)return;
        String selected=movingPackage;
        if(save&&movePreview!=null)getPreferences(MODE_PRIVATE).edit().putString("app_order_v1",String.join("\n",movePreview)).apply();
        movingPackage=null;movePreview=null;moveWaitRelease=false;focusPackage=selected;
        if(feedback)NavigationSound.event(this,save?NavigationSound.CONFIRM:NavigationSound.BACK);
        buildApps();
    }
    private void shiftDirectMove(int dx,int dy){
        long now=SystemClock.uptimeMillis();if(now-lastMoveAt<130)return;lastMoveAt=now;
        int index=movePreview.indexOf(movingPackage);
        int target=AppMovePolicy.target(index,movePreview.size(),cols,dx,dy);
        if(index==target){NavigationSound.event(this,NavigationSound.BUMP);return;}
        Map<String,android.graphics.Rect> before=new HashMap<>();
        List<View> oldCards=new ArrayList<>(dockCards);oldCards.addAll(gridCards);
        for(View card:oldCards){android.graphics.Rect bounds=new android.graphics.Rect();if(card.getGlobalVisibleRect(bounds))before.put(((App)card.getTag()).pkg,bounds);}
        AppOrder.move(movePreview,movingPackage,target);focusPackage=movingPackage;
        NavigationSound.event(this,NavigationSound.TAB);
        buildApps();
        View decor=getWindow().getDecorView();
        decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            public boolean onPreDraw(){
                decor.getViewTreeObserver().removeOnPreDrawListener(this);
                List<View> cards=new ArrayList<>(dockCards);cards.addAll(gridCards);
                for(View card:cards){
                    android.graphics.Rect previous=before.get(((App)card.getTag()).pkg),current=new android.graphics.Rect();
                    if(previous==null||!card.getGlobalVisibleRect(current))continue;
                    float x=previous.left-current.left,y=previous.top-current.top;
                    android.animation.ObjectAnimator ax=android.animation.ObjectAnimator.ofFloat(card,View.TRANSLATION_X,x,0f);
                    android.animation.ObjectAnimator ay=android.animation.ObjectAnimator.ofFloat(card,View.TRANSLATION_Y,y,0f);
                    android.animation.AnimatorSet slide=new android.animation.AnimatorSet();slide.playTogether(ax,ay);
                    slide.setDuration(170);slide.setInterpolator(new DecelerateInterpolator());slide.start();
                }
                return true;
            }
        });
    }

    /** A 16:9 card with the app's banner; the name shows under it while focused. */
    private View tile(App app, boolean inDock) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        card.setTag(R.id.sound_app_card,Boolean.TRUE);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(app.isBanner ? 0xFF1C1C1E : darken(app.color, 0.45f));
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            img.setPadding(dp(22), dp(14), dp(22), dp(14));
        }

        // Focus ring and sheen, drawn above the art.
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(app.pkg.equals(movingPackage)?3:2), 0xE6FFFFFF);
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        ring.setColors(new int[]{0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        View shine = new View(this);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text(11, "sans-serif-medium", Color.WHITE);
        label.setText(app.pkg.equals(movingPackage)?"← "+app.label+" →":app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        if (!inDock) box.addView(label, llp(-1, -2, 0, dp(10), 0, 0));

        card.setOnClickListener(v -> bounceOpen(v, inDock ? 1.12f : 1.1f, () -> {
            if (app.googleTvShortcut) { openGoogleTv(app, v); return; }
            launch(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), v);
        }));
        card.setOnLongClickListener(v -> { startDirectMove(app.pkg); return true; });
        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? (inDock ? 1.12f : 1.1f) : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(14) : 0)
                    .setInterpolator(has && movingPackage==null ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator())
                    .setDuration(has ? 320 : 200).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            label.animate().alpha(has ? 1f : 0f).translationY(has ? dp(4) : 0).setDuration(200).start();
            if (!has) return;
            lastFocusedId = v.getId();
            focusPackage = app.pkg;
            if (inDock) {
                title.setText(app.pkg.equals(movingPackage)?"← "+app.label+" →":app.label);
                title.animate().alpha(1f).setDuration(200).start();
                updateBackdropShade(1f);
                scroll.smoothScrollTo(0, 0);
            } else {
                updateBackdropShade(0.35f);
                title.animate().alpha(0f).setDuration(200).start();
                scrollToRow(box);
            }
        });

        (inDock ? dockCards : gridCards).add(card);
        return box;
    }

    /** Explicit D-pad targets: one row per press, ends of rows and of the top bar don't wrap. */
    private void wireFocus() {
        int d = dockCards.size(), g = gridCards.size();
        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(d > 0 ? dockCards.get(Math.min(d - 1, d / 2)).getId() : p.getId());
        }
        int topId = pills.get(0).getId();
        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(topId);
            c.setNextFocusLeftId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            c.setNextFocusRightId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
            c.setNextFocusDownId(g > 0 ? gridCards.get(Math.min(i, g - 1)).getId() : c.getId());
        }
        int lastRow = (g - 1) / cols;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / cols, col = j % cols;
            c.setNextFocusUpId(r == 0
                    ? (d > 0 ? dockCards.get(Math.min(col, d - 1)).getId() : topId)
                    : gridCards.get(j - cols).getId());
            c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId()
                    : r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusLeftId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusRightId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    private void scrollToRow(View tileBox) {
        View row = (View) tileBox.getParent();
        int y = grid.getTop() + row.getTop();
        int screenH = getResources().getDisplayMetrics().heightPixels;
        // First grid row: slide the dock band up to the top edge; later rows centre on screen.
        View band = (View) dockRow.getParent();
        int target = grid.indexOfChild(row) == 0
                ? hero.getTop() + band.getTop() + dockRow.getTop() - dp(22)
                : y - (int) (screenH * 0.42f);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(target, max)));
    }

    // ---------------------------------------------------------------- inputs

    private void showInputs(View anchor) {
        startActivity(SettingsPopup.position(new Intent(this,InputsActivity.class),anchor));
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    private TextView inputRow(String label, String icon, Runnable action) {
        TextView r = text(13, "sans-serif", Color.WHITE);
        r.setText(label);
        MenuIcon.attach(r, icon, 19, Color.WHITE);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.TRANSPARENT);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0x35FFFFFF : Color.TRANSPARENT);
            bg.setStroke(dp(1), has ? 0xBBFFFFFF : Color.TRANSPARENT);
            r.setTextColor(Color.WHITE);
            MenuIcon.color(r, Color.WHITE);
        });
        return r;
    }

    // ---------------------------------------------------------------- status

    private void applyWidgetVisibility() {
        android.content.SharedPreferences prefs = getSharedPreferences("widget_visibility", 0);
        greeting.setVisibility(prefs.getBoolean("greeting", true) ? View.VISIBLE : View.GONE);
        clockWidget.setVisibility(prefs.getBoolean("clock", true) ? View.VISIBLE : View.GONE);
        date.setVisibility(prefs.getBoolean("date", true) ? View.VISIBLE : View.GONE);
        dateSeparator.setVisibility(prefs.getBoolean("clock", true) && (prefs.getBoolean("date", true) || prefs.getBoolean("greeting", true)) ? View.VISIBLE : View.GONE);
        boolean prayer = prefs.getBoolean("prayer", false);
        prayerWidget.setVisibility(prayer ? View.VISIBLE : View.GONE);
        if (prayer) prayerWidget.resume(); else prayerWidget.pause();
        clock.post(this::alignClockLabels);
    }

    private void showShutdown(){
        if(LauncherOverrideService.showPowerMenu())return;
        if(!LauncherOverrideService.isConnected()){
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                .setTitle(UiText.get(R.string.ui_119))
                .setMessage(UiText.get(R.string.ui_023))
                .setPositiveButton(UiText.get(R.string.ui_032),(d,w)->launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton(UiText.get(R.string.ui_026),null).show();
            GlassControls.dialog(dialog);
        }else android.widget.Toast.makeText(this,UiText.get(R.string.ui_087),android.widget.Toast.LENGTH_LONG).show();
    }

    private void showWidgetSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("widget_visibility", 0);
        LinearLayout panel = NavigationSound.panel(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 0, 0, 0);
        String[] labels = {UiText.get(R.string.ui_111), UiText.get(R.string.ui_067), UiText.get(R.string.ui_044), UiText.get(R.string.ui_066)};
        String[] widgetIcons = {"greeting", "sleep", "calendar", "prayer"};
        String[] keys = {"greeting", "clock", "date", "prayer"};
        android.widget.Switch[] toggles = new android.widget.Switch[keys.length];
        for (int i = 0; i < keys.length; i++) {
            android.widget.Switch toggle = new android.widget.Switch(this);
            toggle.setText(labels[i]);
            toggle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            toggle.setSingleLine(true);
            MenuIcon.attach(toggle, widgetIcons[i], 19, Color.WHITE);
            toggle.setSwitchMinWidth(dp(32));
            toggle.setSwitchPadding(dp(6));
            toggle.setTextSize(12);
            toggle.setPadding(dp(8), dp(4), dp(8), dp(4));
            toggle.setMinHeight(dp(36));
            toggle.setFocusable(true);
            toggle.setChecked(prefs.getBoolean(keys[i], !"prayer".equals(keys[i])));
            toggle.setBackgroundResource(android.R.drawable.list_selector_background);
            toggles[i] = toggle;
            panel.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        }
        android.widget.Button editGreeting = new android.widget.Button(this);
        editGreeting.setText(UiText.get(R.string.ui_136));
        editGreeting.setTag("widget_suboption");
        editGreeting.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        editGreeting.setSingleLine(true);
        editGreeting.setAllCaps(false);
        MenuIcon.attach(editGreeting, "edit", 19, Color.WHITE);
        editGreeting.setVisibility(toggles[0].isChecked() ? View.VISIBLE : View.GONE);
        panel.addView(editGreeting, 1, new LinearLayout.LayoutParams(-1, -2));
        android.widget.Button location = new android.widget.Button(this);
        location.setText(UiText.get(R.string.ui_078));
        location.setTag("widget_suboption");
        location.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        location.setSingleLine(true);
        MenuIcon.attach(location, "location", 19, Color.WHITE);
        location.setAllCaps(false);
        location.setVisibility(toggles[3].isChecked() ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(-1, -2);
        locationParams.leftMargin = dp(16);
        panel.addView(location, locationParams);
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            toggles[i].setOnCheckedChangeListener((button, checked) -> {
                prefs.edit().putBoolean(key, checked).apply();
                applyWidgetVisibility();
                if ("prayer".equals(key)) location.setVisibility(checked ? View.VISIBLE : View.GONE);
                if ("greeting".equals(key)) editGreeting.setVisibility(checked ? View.VISIBLE : View.GONE);
            });
        }
        final PopupWindow[] widgetPopup = new PopupWindow[1];
        location.setOnClickListener(v -> { widgetPopup[0].dismiss(); prayerWidget.settings(); });
        editGreeting.setOnClickListener(v -> { widgetPopup[0].dismiss(); showGreetingEditor(); });
        widgetPopup[0] = SettingsPopup.at(this, orderAnchor, UiText.get(R.string.ui_143), panel, toggles[0],
                () -> { orderPopup=null;orderAnchor.requestFocus(); });
        orderPopup=widgetPopup[0];
    }

    private String greetingSuffix() {
        android.content.SharedPreferences prefs=getSharedPreferences("widget_visibility",MODE_PRIVATE);
        if(!prefs.contains("greeting_suffix")){
            String migrated=GreetingText.suffix(prefs.getString("custom_greeting",""));
            prefs.edit().putString("greeting_suffix",migrated).remove("custom_greeting").apply();
            return migrated;
        }
        return GreetingText.suffix(prefs.getString("greeting_suffix",""));
    }

    private void showGreetingEditor() {
        android.content.SharedPreferences prefs = getSharedPreferences("widget_visibility", MODE_PRIVATE);
        android.widget.EditText input = new android.widget.EditText(this);
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(UiText.get(R.string.ui_089));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        input.setText(greetingSuffix());
        input.setSelection(input.length());
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(UiText.get(R.string.ui_136)).setView(input)
                .setPositiveButton(UiText.get(R.string.ui_120), (d,w) -> {
                    String value = GreetingText.suffix(input.getText().toString());
                    prefs.edit().putString("greeting_suffix", value).remove("custom_greeting").apply();
                    updateClock();
                })
                .setNeutralButton(UiText.get(R.string.ui_036), (d,w) -> {
                    prefs.edit().putString("greeting_suffix", "").remove("custom_greeting").apply();
                    updateClock();
                })
                .setNegativeButton(UiText.get(R.string.ui_026), null).create();
        dialog.show();
        GlassControls.dialog(dialog);
    }

    private void updateClock() {
        if (prayerWidget != null) prayerWidget.update();
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        Locale indonesia = UiText.locale();
        greeting.setText((h < 5 ? UiText.get(R.string.ui_106) : h < 11 ? UiText.get(R.string.ui_107)
                : h < 15 ? UiText.get(R.string.ui_108) : h < 18 ? UiText.get(R.string.ui_109) : UiText.get(R.string.ui_106))
                + (greetingSuffix().isEmpty()?"":", "+greetingSuffix()));
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
        date.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", indonesia).format(now.getTime()).toUpperCase(indonesia));
        clock.post(this::alignClockLabels);
    }

    private void alignClockLabels() {
        if (clock == null || date == null || dateSeparator == null || clockLabels == null) return;
        if (greeting.getVisibility() == View.VISIBLE && date.getVisibility() == View.VISIBLE
                && greeting.length() > 0 && date.length() > 0) {
            android.graphics.Rect greetingEdge = new android.graphics.Rect();
            android.graphics.Rect dateEdge = new android.graphics.Rect();
            String greetingText = greeting.getText().toString();
            String dateText = date.getText().toString();
            greeting.getPaint().getTextBounds(greetingText, 0, greetingText.length(), greetingEdge);
            date.getPaint().getTextBounds(dateText, 0, dateText.length(), dateEdge);
            greeting.setTranslationX(dateEdge.left - greetingEdge.left - 0.5f * getResources().getDisplayMetrics().density);
        } else {
            greeting.setTranslationX(0f);
        }
        android.view.ViewGroup.LayoutParams labelParams = clockLabels.getLayoutParams();
        if (clockWidget.getVisibility() != View.VISIBLE) {
            if (labelParams.height != -2) { labelParams.height = -2; clockLabels.setLayoutParams(labelParams); }
            greeting.setTranslationY(0f);
            date.setTranslationY(0f);
            dateSeparator.setTranslationY(0f);
            return;
        }
        if (clock.getBaseline() < 0 || clock.getHeight() == 0) return;
        if (labelParams.height != clock.getHeight()) {
            labelParams.height = clock.getHeight();
            clockLabels.setLayoutParams(labelParams);
            clockLabels.post(this::alignClockLabels);
            return;
        }
        android.graphics.Rect bounds = new android.graphics.Rect();
        String digits = clock.getText().toString();
        if (digits.isEmpty()) return;
        clock.getPaint().getTextBounds(digits, 0, digits.length(), bounds);
        float clockTop = clockWidget.getTop() + clock.getTop() + clock.getBaseline() + bounds.top;
        float clockBottom = clockWidget.getTop() + clock.getTop() + clock.getBaseline() + bounds.bottom;
        float bottomOffset = 0.5f * getResources().getDisplayMetrics().density;
        android.view.ViewGroup.LayoutParams pipeParams = dateSeparator.getLayoutParams();
        int pipeHeight = Math.max(1, Math.round(clockBottom - bottomOffset - clockTop));
        if (pipeParams.height != pipeHeight) {
            pipeParams.height = pipeHeight;
            dateSeparator.setLayoutParams(pipeParams);
        }
        dateSeparator.setTranslationY(clockTop - dateSeparator.getTop());
        android.graphics.Rect greetingInk = new android.graphics.Rect();
        android.graphics.Rect dateInk = new android.graphics.Rect();
        String greetingValue = greeting.getText().toString();
        String dateValue = date.getText().toString();
        boolean hasGreeting = greeting.getVisibility() == View.VISIBLE && !greetingValue.isEmpty();
        boolean hasDate = date.getVisibility() == View.VISIBLE && !dateValue.isEmpty();
        if (hasGreeting) greeting.getPaint().getTextBounds(greetingValue, 0, greetingValue.length(), greetingInk);
        if (hasDate) date.getPaint().getTextBounds(dateValue, 0, dateValue.length(), dateInk);
        float blockHeight = (hasGreeting ? greetingInk.height() : 0) + (hasDate ? dateInk.height() : 0)
                + (hasGreeting && hasDate ? dp(6) : 0);
        float blockTop = clockTop + (clockBottom - clockTop - blockHeight) / 2f;
        for (TextView label : new TextView[]{greeting, date}) {
            String value = label.getText().toString();
            if (label.getVisibility() != View.VISIBLE || value.isEmpty() || label.getBaseline() < 0) continue;
            label.getPaint().getTextBounds(value, 0, value.length(), bounds);
            float origin = clockLabels.getTop() + label.getTop() + label.getBaseline();
            float labelTop = label == greeting ? blockTop
                    : blockTop + (hasGreeting ? greetingInk.height() + dp(6) : 0);
            label.setTranslationY(labelTop - origin - bounds.top);
        }
    }

    private void rememberOfflineNetwork(){
        String last=getSharedPreferences("network_state",MODE_PRIVATE).getString("last_ssid","");
        networkPopupLabel="Offline"+(last.isEmpty()?"":" - "+last);
    }

    private void updateWifi() {
        try {
            if (connectivity == null) connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (connectivity == null) { rememberOfflineNetwork();wifiPill.setText(UiText.get(R.string.ui_126)); return; }
            Network active = connectivity.getActiveNetwork();
            if (active == null) { rememberOfflineNetwork();wifiPill.setText("Offline"); return; }
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(active);
            if (caps == null) { rememberOfflineNetwork();wifiPill.setText(UiText.get(R.string.ui_084)); return; }
            showNetworkStatus(caps);
        } catch (SecurityException ignored) { rememberOfflineNetwork();wifiPill.setText(UiText.get(R.string.ui_126)); }
    }

    private void showNetworkStatus(NetworkCapabilities caps) {
        String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "LAN"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : UiText.get(R.string.ui_068);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            String name = "";
            try {
                name=TvPlatform.wifiName(caps);
                if (name.isEmpty()) {
                    WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    if (manager != null && manager.getConnectionInfo() != null)
                        name = WifiName.clean(manager.getConnectionInfo().getSSID());
                }
            } catch (SecurityException ignored) { }
            if (!name.isEmpty()) {
                transport = name;
                android.content.SharedPreferences history=getSharedPreferences("network_state",MODE_PRIVATE);
                if(!name.equals(history.getString("last_ssid","")))history.edit().putString("last_ssid",name).apply();
            }
        }
        wifiPill.setText(transport);
        networkPopupLabel="Online - "+transport;
    }

    // ---------------------------------------------------------------- glow

    private void updateBackdropShade(float alpha) {
        // The grid sits over the photo's busiest middle part, so dim it while browsing apps.
        gridShade.animate().alpha(alpha < 1f ? 1f : 0f).setDuration(300).start();
    }

    // ---------------------------------------------------------------- wallpaper

    private ImageView wallpaperLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    /** Decode the next photo off the main thread, then crossfade it in over the current one. */
    private void nextWallpaper() {
        handler.removeCallbacks(rotateWallpaper);
        if (wallpapers.length == 0) return;
        final int request = ++wallpaperRequest;
        if (wallIndex < 0) wallIndex = getPreferences(MODE_PRIVATE).getInt("wallpaper", -1);
        wallIndex = (wallIndex + 1) % wallpapers.length;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", wallIndex).apply();
        wallChangedAt = SystemClock.elapsedRealtime();
        String name = "wallpapers/" + wallpapers[wallIndex];
        new Thread(() -> {
            Bitmap bm = null;
            String movieTitle = "";
            if (MovieWallpaperProvider.enabled(this)) {
                MovieWallpaperProvider.Result movie = MovieWallpaperProvider.next(this);
                if (movie != null) { bm = movie.bitmap; movieTitle = movie.title; }
            }
            if (bm == null) try (java.io.InputStream in = getAssets().open(name)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeStream(in, null, o);
            } catch (java.io.IOException | RuntimeException error) {
                android.util.Log.w("TVHomeWallpaper", "Cannot load " + name, error);
            }
            Bitmap result = bm;
            String caption = movieTitle;
            runOnUiThread(() -> {
                if (request != wallpaperRequest || isDestroyed()) {
                    if (result != null) result.recycle();
                    return;
                }
                showWallpaper(result);
                if (result != null) {
                    wallpaperCaption.setText(caption);
                    wallpaperCaption.setVisibility(caption.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        }).start();
        scheduleWallpaper(WALLPAPER_INTERVAL_MS);
    }

    private void showWallpaper(Bitmap bm) {
        if (bm == null || isDestroyed()) return;
        // Keep both image layers in a fixed position behind the content.
        // Show the first decoded image immediately, independently of animation state.
        wallFront.animate().cancel();
        wallBack.animate().cancel();
        if (!wallpaperLoaded) {
            wallBack.setImageBitmap(bm);
            wallBack.setAlpha(1f);
            wallFront.setImageDrawable(null);
            wallFront.setAlpha(0f);
            wallpaperLoaded = true;
            return;
        }
        // Preserve the currently visible frame if a new request interrupts a transition.
        if (wallFront.getDrawable() != null) wallBack.setImageDrawable(wallFront.getDrawable());
        wallFront.setImageBitmap(bm);
        wallFront.setAlpha(0f);
        wallFront.animate().alpha(1f).setDuration(1200).withEndAction(() -> {
            wallBack.setImageBitmap(bm);
            wallBack.setAlpha(1f);
            wallFront.setAlpha(0f);
            wallFront.setImageDrawable(null);
        }).start();
    }

    private void scheduleWallpaper(long delay) {
        handler.removeCallbacks(rotateWallpaper);
        handler.postDelayed(rotateWallpaper, Math.max(1000, delay));
    }

    /** Average the banner's vivid pixels, then push saturation so the glow reads as a colour. */
    private int sampleColor(String pkg, Drawable d) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        int color = 0xFF3A3A3A;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.75f); hsv[2] = 0.85f; }
                else { hsv[1] = 0f; hsv[2] = 0.45f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        colorCache.put(pkg, color);
        return color;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        View header = hero.getChildAt(0);
        header.setAlpha(0f);
        header.setTranslationY(-dp(12));
        header.animate().alpha(1f).translationY(0).setDuration(500).setStartDelay(80).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setDuration(500).setStartDelay(200).start();
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View t = dockRow.getChildAt(i);
            t.setAlpha(0f);
            t.setTranslationY(dp(24));
            t.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(450).setStartDelay(160 + i * 60L).start();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void bounceOpen(View card, float restScale, Runnable open) {
        if (appLaunchBounce != null || appLaunchCard != null) return;
        card.animate().cancel();
        appLaunchDispatched = false;
        appLaunchCard = card;
        appLaunchRestScale = restScale;
        android.animation.Keyframe start = android.animation.Keyframe.ofFloat(0f, card.getScaleX());
        android.animation.Keyframe press = android.animation.Keyframe.ofFloat(0.35f, restScale * 0.96f);
        android.animation.Keyframe expand = android.animation.Keyframe.ofFloat(1f, restScale * 1.08f);
        press.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        expand.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        android.animation.ValueAnimator bounce = android.animation.ValueAnimator.ofPropertyValuesHolder(
                android.animation.PropertyValuesHolder.ofKeyframe("scale", start, press, expand));
        appLaunchBounce = bounce;
        bounce.setDuration(260);
        bounce.setInterpolator(new android.view.animation.LinearInterpolator());
        final boolean[] opening = {false};
        bounce.addUpdateListener(animation -> {
            float scale = (Float) animation.getAnimatedValue();
            card.setScaleX(scale);card.setScaleY(scale);
            // Start the destination during the expansion, overlapping the remaining bounce.
            if (!opening[0] && animation.getAnimatedFraction() >= 0.55f) {
                opening[0] = true;
                try {
                    if (!isFinishing() && !isDestroyed()) open.run();
                } finally {
                    if (!appLaunchDispatched) cancelAppLaunchBounce();
                }
            }
        });
        bounce.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (appLaunchBounce != bounce) return;
                appLaunchBounce = null;
                // Preserve the outgoing card until the launcher is shown again.
                if (!appLaunchDispatched) cancelAppLaunchBounce();
            }
        });
        bounce.start();
    }

    private void cancelAppLaunchBounce() {
        appLaunchDispatched = false;
        if (appLaunchBounce != null) {
            appLaunchBounce.removeAllListeners();appLaunchBounce.cancel();appLaunchBounce = null;
        }
        if (appLaunchCard != null) {
            float scale = appLaunchCard.hasFocus() ? appLaunchRestScale : 1f;
            appLaunchCard.setScaleX(scale);appLaunchCard.setScaleY(scale);appLaunchCard = null;
        }
    }

    private boolean launch(Intent i) { return launch(i, null); }

    private boolean launch(Intent i, View origin) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle options = null;
            if (origin != null && origin.isAttachedToWindow() && origin.getWidth() > 0 && origin.getHeight() > 0) {
                // Native popup reveal preserves the TCL system transition.
                android.graphics.Rect visible = new android.graphics.Rect();
                int[] position = new int[2];origin.getLocationOnScreen(position);
                if (origin.getGlobalVisibleRect(visible)) {
                    options = ActivityOptions.makeClipRevealAnimation(origin,
                            visible.left - position[0], visible.top - position[1],
                            visible.width(), visible.height()).toBundle();
                }
            }
            startActivity(i, options);
            if (origin != null) NavigationSound.event(this,NavigationSound.LAUNCH);
            if (appLaunchCard != null) appLaunchDispatched = true;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openGoogleTv(App app, View origin) {
        try {
            ActivityInfo info = getPackageManager().getActivityInfo(app.component, PackageManager.MATCH_DISABLED_COMPONENTS);
            if (!info.enabled || !info.applicationInfo.enabled) { openGoogleTvAppSettings(app); return; }
            LauncherOverrideService.POLICY.allowGoogleVisit();
            if (launch(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME).setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), origin)) return;
        } catch (PackageManager.NameNotFoundException ignored) { }
        LauncherOverrideService.POLICY.returnToTvHome();
        openGoogleTvAppSettings(app);
    }

    private void openGoogleTvAppSettings(App app) {
        LauncherOverrideService.POLICY.returnToTvHome();
        if (launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:" + app.pkg)))) return;
        new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_041))
                .setMessage(UiText.get(R.string.ui_033))
                .setPositiveButton(UiText.get(R.string.ui_116), (dialog, which) -> launch(new Intent(Settings.ACTION_SETTINGS)))
                .setNegativeButton(UiText.get(R.string.ui_135), null).show();
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if(movingPackage!=null){
            int key=e.getKeyCode();
            boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
            if(confirm){
                if(e.getAction()==KeyEvent.ACTION_UP){
                    if(moveWaitRelease)moveWaitRelease=false;
                    else if(!e.isCanceled())finishDirectMove(true,true);
                }
                return true;
            }
            if(key==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())finishDirectMove(false,true);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(e.getAction()==KeyEvent.ACTION_DOWN){moveWaitRelease=false;shiftDirectMove(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);}
                return true;
            }
            if(key==KeyEvent.KEYCODE_MENU)return true;
        }
        // Menu key on the remote opens the Inputs dropdown.
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !pills.isEmpty()) {
            showInputs(pills.get(0));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** 16:9 container. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** ScrollView that never auto-scrolls to the focused child; the activity scrolls it by row. */
    static final class HomeScrollView extends android.widget.ScrollView {
        HomeScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }
}

