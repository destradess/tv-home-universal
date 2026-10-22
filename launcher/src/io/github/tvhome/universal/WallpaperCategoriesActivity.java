package io.github.tvhome.universal;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
public class WallpaperCategoriesActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        int[] labels={R.string.category_movie,R.string.category_tv,R.string.category_anime,R.string.category_top_movie,R.string.category_top_tv,R.string.category_top_anime};
        String[] icons={"wallpaper","display","wallpaper","wallpaper","display","wallpaper"};
        Switch first=null;
        for(int i=0;i<labels.length;i++){
            final int index=i;Switch toggle=new Switch(this);
            toggle.setText(UiText.get(labels[i]));toggle.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            MenuIcon.attach(toggle,icons[i],19,Color.WHITE);
            toggle.setChecked((MovieWallpaperProvider.categoryMask(this)&(1<<i))!=0);
            toggle.setOnCheckedChangeListener((button,checked)->{
                if(!MovieWallpaperProvider.setCategory(this,index,checked)){
                    button.setChecked(true);Toast.makeText(this,UiText.get(R.string.category_keep_one),Toast.LENGTH_SHORT).show();
                }
            });
            panel.addView(toggle,new LinearLayout.LayoutParams(-1,Math.round(36*getResources().getDisplayMetrics().density)));
            if(first==null)first=toggle;
        }
        SettingsPopup.show(this,UiText.get(R.string.wallpaper_categories),panel,first);
    }
}
