package io.github.tvhome.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

final class GlassMenu {
    static Drawable background(Context context){
        float density=context.getResources().getDisplayMetrics().density;
        GradientDrawable shade=new GradientDrawable();shade.setCornerRadius(28*density);shade.setColor(0xB31D1D1D);
        return new LayerDrawable(new Drawable[]{shade,new DockGlassDrawable(density)});
    }
    static void show(Activity activity,String title,String[] items,DialogInterface.OnClickListener action,String close){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setItems(items,action)
                .setNegativeButton(close,null).create();
        dialog.setOnDismissListener(d->NavigationSound.event(activity,NavigationSound.CLOSE));
        dialog.show();NavigationSound.event(activity,NavigationSound.OPEN);
        GlassControls.dialog(dialog);
        if(dialog.getWindow()!=null){NavigationSound.attach(dialog.getWindow().getDecorView());NavigationSound.attachWindow(dialog.getWindow());}
        float density=activity.getResources().getDisplayMetrics().density;
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(background(activity));
            dialog.getWindow().setDimAmount(0.18f);
            dialog.getWindow().setLayout(Math.min((int)(480*density),(int)(activity.getResources().getDisplayMetrics().widthPixels*0.8f)),-2);
        }
        if(dialog.getListView()!=null){
            StateListDrawable selector=new StateListDrawable();
            for(int state:new int[]{android.R.attr.state_pressed,android.R.attr.state_focused,android.R.attr.state_selected}){
                GradientDrawable fill=new GradientDrawable();fill.setColor(0x40FFFFFF);fill.setCornerRadius(10*density);
                selector.addState(new int[]{state},fill);
            }
            selector.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0x00000000));
            dialog.getListView().setSelector(selector);
            dialog.getListView().setDrawSelectorOnTop(false);
        }
    }
}
