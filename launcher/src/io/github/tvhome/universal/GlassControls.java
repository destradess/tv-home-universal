package io.github.tvhome.universal;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Neutral glass controls with a visible remote focus ring. */
final class GlassControls {
    private static final int WHITE=0xEEF4F4F4;
    private static ColorStateList tones(int normal,int active){
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{active,normal});
    }
    static android.graphics.drawable.Drawable surface(View view){
        float d=view.getResources().getDisplayMetrics().density;
        StateListDrawable states=new StateListDrawable();
        for(int state:new int[]{android.R.attr.state_focused,android.R.attr.state_pressed,0}){
            GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                state==0?new int[]{0x16FFFFFF,0x08FFFFFF}:new int[]{0x48FFFFFF,0x22FFFFFF});
            glass.setCornerRadius(12*d);glass.setStroke(Math.max(1,Math.round(d)),state==0?0x28FFFFFF:0xBBFFFFFF);
            states.addState(state==0?new int[]{}:new int[]{state},glass);
        }
        return states;
    }
    static void focusOnlyRows(View view){
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)focusOnlyRows(group.getChildAt(i));
        }
        if(view instanceof Button || view instanceof Switch || view instanceof SeekBar){
            float d=view.getResources().getDisplayMetrics().density;
            StateListDrawable background=new StateListDrawable();
            for(int state:new int[]{android.R.attr.state_focused,android.R.attr.state_pressed}){
                GradientDrawable highlight=new GradientDrawable();
                highlight.setColor(0x35FFFFFF);highlight.setCornerRadius(12*d);
                highlight.setStroke(Math.max(1,Math.round(d)),0xBBFFFFFF);
                background.addState(new int[]{state},highlight);
            }
            background.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0x00000000));
            view.setBackground(background);
        }
    }
    static void apply(View view){
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i));}
        if(view instanceof TextView)((TextView)view).setTextColor(WHITE);
        if(view instanceof Switch){
            Switch toggle=(Switch)view;toggle.setThumbTintList(tones(0xFF989898,0xFFE4E4E4));
            toggle.setTrackTintList(tones(0x40777777,0x807F7F7F));toggle.setBackground(surface(view));
        }else if(view instanceof SeekBar){
            SeekBar slider=(SeekBar)view;slider.setProgressTintList(ColorStateList.valueOf(0xBBDDDDDD));
            slider.setProgressBackgroundTintList(ColorStateList.valueOf(0x35FFFFFF));
            slider.setThumbTintList(ColorStateList.valueOf(0xFFE4E4E4));slider.setBackground(surface(view));
        }else if(view instanceof Button){
            Button button=(Button)view;button.setBackgroundTintList(null);button.setBackground(surface(view));
            button.setStateListAnimator(null);button.setAllCaps(false);
        }else if(view instanceof EditText){
            EditText field=(EditText)view;field.setBackgroundTintList(ColorStateList.valueOf(0x88DDDDDD));field.setHintTextColor(0x99FFFFFF);
        }
    }
    static void dialog(android.app.AlertDialog dialog){
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(GlassMenu.background(dialog.getContext()));dialog.getWindow().setDimAmount(0.18f);apply(dialog.getWindow().getDecorView());}
    }
}
