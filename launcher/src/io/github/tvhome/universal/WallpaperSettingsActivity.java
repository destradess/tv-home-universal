package io.github.tvhome.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

public class WallpaperSettingsActivity extends Activity {
    private Switch online;
    private WebView logo;
    private Button sourceButton, categoriesButton;
    private LinearLayout wallpaperPanel;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);wallpaperPanel=panel;
        online=new Switch(this);online.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));MenuIcon.attach(online,"wallpaper",19,Color.WHITE);online.setText(UiText.get(R.string.ui_140));online.setTextSize(12);online.setPadding(dp(8),dp(4),dp(8),dp(4));
        online.setChecked(MovieWallpaperProvider.enabled(this));online.setBackgroundResource(android.R.drawable.list_selector_background);
        panel.addView(online,new LinearLayout.LayoutParams(-1,dp(40)));
        online.setOnCheckedChangeListener((button,enabled)->{
            if(enabled && WallpaperSources.requiresKey(WallpaperSources.selected(this)) && WallpaperSources.key(this,WallpaperSources.selected(this)).isEmpty()){
                online.setChecked(false);Toast.makeText(this,UiText.get(R.string.ui_055),Toast.LENGTH_SHORT).show();return;
            }
            MovieWallpaperProvider.configure(this,enabled,null);
        });
        add(panel,UiText.get(R.string.ui_014),"key",this::api);
        categoriesButton=add(panel,UiText.get(R.string.wallpaper_categories),"widgets",()->{
            if(!WallpaperSources.selected(this).equals("tmdb")){message(UiText.get(R.string.category_tmdb_only));return;}
            android.content.Intent categories=new android.content.Intent(this,WallpaperCategoriesActivity.class);
            if(getIntent().hasExtra("popup_x"))categories.putExtra("popup_x",getIntent().getIntExtra("popup_x",0));
            if(getIntent().hasExtra("popup_y"))categories.putExtra("popup_y",getIntent().getIntExtra("popup_y",0));
            startActivity(categories);
        });
        sourceButton=add(panel,UiText.get(R.string.ui_016)+WallpaperSources.name(WallpaperSources.selected(this)),"info",this::sources);
        updateCategoriesVisibility();
        sourceButton.setOnLongClickListener(v->{credits();return true;});
        SettingsPopup.show(this,UiText.get(R.string.ui_140),panel,online);
    }
    private void updateCategoriesVisibility(){
        // Only TMDB currently supports the five film/series/anime filters.
        categoriesButton.setVisibility(WallpaperSources.selected(this).equals("tmdb")?android.view.View.VISIBLE:android.view.View.GONE);
        if(wallpaperPanel.getParent() instanceof android.widget.ScrollView){
            android.widget.ScrollView scroll=(android.widget.ScrollView)wallpaperPanel.getParent();
            wallpaperPanel.measure(android.view.View.MeasureSpec.makeMeasureSpec(scroll.getWidth(),android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
            android.view.ViewGroup.LayoutParams params=scroll.getLayoutParams();
            params.height=Math.min(dp(240),Math.max(dp(36),wallpaperPanel.getMeasuredHeight()));
            scroll.setLayoutParams(params);
        }
    }
    private void sources(){
        String[] labels=new String[WallpaperSources.IDS.length];
        for(int i=0;i<labels.length;i++)labels[i]=WallpaperSources.NAMES[i]+(WallpaperSources.unavailable(WallpaperSources.IDS[i]).isEmpty()?"":UiText.get(R.string.ui_002));
        GlassMenu.show(this,UiText.get(R.string.ui_015),labels,(dialog,index)->{
            String source=WallpaperSources.IDS[index],reason=WallpaperSources.unavailable(source);
            if(!reason.isEmpty()){message(reason);return;}
            WallpaperSources.select(this,source);
            sourceButton.setText(UiText.get(R.string.ui_016)+WallpaperSources.name(source));
            updateCategoriesVisibility();
            if(WallpaperSources.requiresKey(source)&&WallpaperSources.key(this,source).isEmpty()){
                online.setChecked(false);MovieWallpaperProvider.configure(this,false,null);
                Toast.makeText(this,UiText.get(R.string.ui_054)+WallpaperSources.keyLabel(source)+UiText.get(R.string.ui_008),Toast.LENGTH_LONG).show();
            }
        },UiText.get(R.string.ui_135));
    }
    private void message(String text){AlertDialog dialog=new AlertDialog.Builder(this).setMessage(text).setPositiveButton(UiText.get(R.string.ui_135),null).show();GlassControls.dialog(dialog);}
    private void api(){
        final String source=WallpaperSources.selected(this);
        if(!WallpaperSources.acceptsKey(source)){message(WallpaperSources.name(source)+UiText.get(R.string.ui_005));return;}

        EditText field=new EditText(this);field.setSingleLine(true);
        field.setHint(WallpaperSources.keyLabel(source));field.setTextSize(13);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(WallpaperSources.keyLabel(source)).setView(field)
                .setPositiveButton(UiText.get(R.string.ui_120),null).setNeutralButton(UiText.get(R.string.ui_043),(d,w)->{
                    WallpaperSources.saveKey(this,source,"");
                    if(WallpaperSources.requiresKey(source)){MovieWallpaperProvider.configure(this,false,null);online.setChecked(false);}
                }).setNegativeButton(UiText.get(R.string.ui_026),null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            try{
                String entered=field.getText().toString().trim();
                String value=entered.isEmpty()?WallpaperSources.key(this,source):entered;
                if(source.equals("tmdb")||source.equals("jikan"))value=MovieBackdropRules.normalizeCredential(value);
                else if(!value.matches("[A-Za-z0-9._~+/=:-]{1,4096}"))throw new IllegalArgumentException(UiText.get(R.string.ui_038));
                if(value.isEmpty()){field.setError(UiText.get(R.string.ui_056));return;}
                WallpaperSources.saveKey(this,source,value);MovieWallpaperProvider.configure(this,true,null);online.setChecked(true);field.setText("");dialog.dismiss();
            }catch(IllegalArgumentException error){field.setError(UiText.get(R.string.ui_038));}
        }));dialog.show();GlassControls.dialog(dialog);
    }
    private void credits(){
        String source=WallpaperSources.selected(this);
        if(!source.equals("tmdb")){
            android.widget.TextView text=new android.widget.TextView(this);text.setTextSize(13);text.setPadding(dp(16),dp(8),dp(16),dp(8));
            String credit=MovieWallpaperProvider.prefs(this).getString("current_source","").equals(source)?MovieWallpaperProvider.prefs(this).getString("current_credit","")+"\n"+MovieWallpaperProvider.prefs(this).getString("current_page",""):UiText.get(R.string.ui_027);
            text.setText(WallpaperSources.name(source)+"\n\n"+credit);text.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);text.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            AlertDialog info=new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_076)).setView(text).setPositiveButton(UiText.get(R.string.ui_135),null).show();GlassControls.dialog(info);return;
        }

        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(8));
        if(logo!=null){logo.destroy();logo=null;}
        logo=new WebView(this);logo.setBackgroundColor(Color.TRANSPARENT);logo.setFocusable(false);logo.getSettings().setJavaScriptEnabled(false);
        logo.loadDataWithBaseURL("file:///android_asset/","<html><body style='margin:0;background:transparent'><img src='tmdb-logo.svg' style='width:180px;height:auto' alt='TMDB'></body></html>","text/html","UTF-8",null);
        content.addView(logo,new LinearLayout.LayoutParams(dp(200),dp(36)));
        android.widget.TextView note=new android.widget.TextView(this);note.setTextSize(12);note.setText(UiText.get(R.string.ui_131));content.addView(note);
        AlertDialog credits=new AlertDialog.Builder(this).setTitle(UiText.get(R.string.ui_017)).setView(content).setPositiveButton(UiText.get(R.string.ui_135),null).show();GlassControls.dialog(credits);
    }
    private Button add(LinearLayout panel,String label,String icon,Runnable action){Button button=new Button(this);button.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));MenuIcon.attach(button,icon,19,Color.WHITE);button.setText(label);button.setTextSize(12);button.setAllCaps(false);button.setOnClickListener(v->action.run());panel.addView(button,new LinearLayout.LayoutParams(-1,dp(40)));return button;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(logo!=null)logo.destroy();super.onDestroy();}
}
