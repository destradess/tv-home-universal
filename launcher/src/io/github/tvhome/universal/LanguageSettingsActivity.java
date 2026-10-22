package io.github.tvhome.universal;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.graphics.Typeface;
import android.widget.*;
import java.util.*;

public class LanguageSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getIntent().putExtra("popup_quick_position",true);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        List<Locale> languages=SystemLanguages.available();Locale current=UiText.locale();
        android.view.View selected=null;
        for(Locale locale:languages){
            String label=locale.getDisplayName(locale);
            Button row=new Button(this);row.setText((locale.equals(current)?"✓ ":"")+label);
            row.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setContentDescription(label+" ("+locale.toLanguageTag()+")");
            MenuIcon.attach(row,"language",19,0xFFFFFFFF);
            row.setOnClickListener(v->choose(locale));panel.addView(row,new LinearLayout.LayoutParams(-1,dp(36)));
            if(locale.equals(current))selected=row;
        }
        Button system=new Button(this);system.setText(UiText.get(R.string.language_system_settings));
        system.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));MenuIcon.attach(system,"settings",19,0xFFFFFFFF);
        system.setOnClickListener(v->openSystem());panel.addView(system,new LinearLayout.LayoutParams(-1,dp(36)));
        SettingsPopup.show(this,UiText.get(R.string.language_title),panel,selected);
    }
    private void choose(Locale locale){
        if(!SystemLanguages.permitted(this)){
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(UiText.get(R.string.language_title))
                .setMessage(UiText.get(R.string.language_permission_needed))
                .setPositiveButton(UiText.get(R.string.language_system_settings),(d,w)->openSystem())
                .setNegativeButton(UiText.get(R.string.ui_026),null).show();GlassControls.dialog(dialog);return;
        }
        try{SystemLanguages.apply(this,locale);finish();}
        catch(Exception error){
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(UiText.get(R.string.language_title))
                .setMessage(UiText.get(R.string.language_change_failed))
                .setPositiveButton(UiText.get(R.string.language_system_settings),(d,w)->openSystem())
                .setNegativeButton(UiText.get(R.string.ui_026),null).show();GlassControls.dialog(dialog);
        }
    }
    private void openSystem(){try{startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));finish();}catch(RuntimeException e){Toast.makeText(this,UiText.get(R.string.language_change_failed),Toast.LENGTH_LONG).show();}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
