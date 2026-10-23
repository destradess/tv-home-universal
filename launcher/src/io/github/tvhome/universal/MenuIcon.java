package io.github.tvhome.universal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/** Small original stroke icons, independent of fonts and emoji rendering. */
final class MenuIcon extends Drawable {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;
    MenuIcon(String kind,int color){this.kind=kind;this.color=color;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    static void attach(TextView view,String kind,int dp,int color){
        float density=view.getResources().getDisplayMetrics().density;
        MenuIcon icon=new MenuIcon(kind,color);int size=Math.round(dp*density);icon.setBounds(0,0,size,size);
        view.setCompoundDrawables(icon,null,null,null);view.setCompoundDrawablePadding(Math.round(8*density));
    }
    static void color(TextView view,int color){for(Drawable d:view.getCompoundDrawables())if(d instanceof MenuIcon){((MenuIcon)d).color=color;d.invalidateSelf();}}
    @Override public void draw(Canvas c){
        Rect b=getBounds();int save=c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);paint.setColor(color);
        switch(kind){
            case "status_active":
                c.drawCircle(12,12,9,paint);path(c,7,12,10,15,17,8);break;
            case "status_inactive":
                c.drawCircle(12,12,9,paint);path(c,8,12,16,12);break;
            case "status_waiting":
                c.drawCircle(12,12,9,paint);path(c,12,6,12,12,16,14);break;
            case "watch_timer":
                c.drawCircle(12,14,8,paint);path(c,9,2,15,2);path(c,12,2,12,5);path(c,12,9,12,14,16,14);path(c,19,5,21,7);break;
            case "power":
                c.drawArc(4,4,20,21,-45,270,false,paint);path(c,12,2,12,11);break;
            case "power_off":
                c.drawArc(4,4,20,21,-45,270,false,paint);path(c,12,2,12,11);path(c,3,3,21,21);break;
            case "accessibility":
                c.drawCircle(12,4,2,paint);path(c,4,9,12,11,20,9);path(c,12,11,12,15,7,22);path(c,12,15,17,22);break;
            case "key":
                c.drawCircle(8,8,5,paint);path(c,11.5f,11.5f,21,21);path(c,17,17,20,14);path(c,19,19,22,16);break;
            case "info":
                c.drawCircle(12,12,9,paint);path(c,12,11,12,17);c.drawCircle(12,7,0.7f,paint);break;
            case "greeting":
                c.drawCircle(12,12,4,paint);
                for(int i=0;i<8;i++){int r=c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5,paint);c.restoreToCount(r);}break;
            case "calendar":
                c.drawRoundRect(3,5,21,21,2,2,paint);path(c,3,10,21,10);path(c,8,2,8,7);path(c,16,2,16,7);
                path(c,7,14,10,14);path(c,14,14,17,14);path(c,7,18,10,18);break;
            case "prayer":
                path(c,3,21,21,21);path(c,5,21,5,12,19,12,19,21);
                curve(c,6,12,6,7,12,4);curve(c,12,4,18,7,18,12);
                path(c,10,21,10,16,14,16,14,21);path(c,3,21,3,7);path(c,21,21,21,7);break;
            case "location":
                Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(9,18,4,13,4,9);pin.cubicTo(4,-1,20,-1,20,9);pin.cubicTo(20,13,15,18,12,22);c.drawPath(pin,paint);c.drawCircle(12,9,3,paint);break;
            case "save":
                path(c,4,3,17,3,21,7,21,21,3,21,3,3,4,3);
                path(c,7,3,7,10,16,10,16,3);path(c,7,21,7,14,17,14,17,21);break;
            case "edit":
                path(c,4,16,15,5,19,9,8,20,3,21,4,16);
                path(c,13,7,17,11);path(c,16,4,18,2,22,6,20,8);break;
            case "language":
                c.drawCircle(12,12,9,paint);path(c,3,12,21,12);
                c.drawOval(8,3,16,21,paint);path(c,5,7,19,7);path(c,5,17,19,17);break;
            case "input":
                path(c,13,4,20,4,20,20,13,20);path(c,3,12,15,12);path(c,10,7,15,12,10,17);break;
            case "network":
                c.drawRoundRect(4,3,20,9,1,1,paint);path(c,12,9,12,14,4,14,4,18);path(c,12,14,20,14,20,18);
                c.drawRect(2,18,6,22,paint);c.drawRect(18,18,22,22,paint);break;
            case "refresh":
                c.drawArc(4,4,20,20,40,270,false,paint);path(c,16,2,20,6,15,8);break;
            case "home":
                path(c,3,11,12,3,21,11);path(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
            case "wallpaper":
                c.drawRoundRect(3,3,21,21,2,2,paint);c.drawCircle(8,8,1.6f,paint);path(c,4,17,9,12,13,16,17,10,21,15);break;
            case "apps":
                c.drawRoundRect(2,4,22,20,2,2,paint);
                path(c,8,4,8,20);path(c,8,12,22,12);path(c,15,12,15,20);break;
            case "layout_horizontal":
                c.drawRoundRect(2,7,8,17,1.5f,1.5f,paint);
                c.drawRoundRect(11,7,17,17,1.5f,1.5f,paint);
                path(c,21,7,22,7,22,17,21,17);break;
            case "layout_classic":
                c.drawRoundRect(2,3,22,9,1.5f,1.5f,paint);
                c.drawRoundRect(2,13,7,21,1,1,paint);
                c.drawRoundRect(9.5f,13,14.5f,21,1,1,paint);
                c.drawRoundRect(17,13,22,21,1,1,paint);break;
            case "widgets":
                c.drawRoundRect(3,3,10,10,1.5f,1.5f,paint);c.drawRoundRect(14,3,21,10,1.5f,1.5f,paint);
                c.drawRoundRect(3,14,10,21,1.5f,1.5f,paint);c.drawRoundRect(14,14,21,21,1.5f,1.5f,paint);break;
            case "quick":
                path(c,3,6,21,6);path(c,3,12,21,12);path(c,3,18,21,18);
                c.drawCircle(8,6,2,paint);c.drawCircle(16,12,2,paint);c.drawCircle(10,18,2,paint);break;
            case "sleep":
                c.drawCircle(12,12,9,paint);path(c,12,6,12,12,16,14);break;
            case "hdmi":
                path(c,3,5,21,5,21,12,17,17,7,17,3,12,3,5);
                path(c,8,17,8,21,16,21,16,17);
                for(int i=0;i<4;i++)c.drawLine(7+i*3,8,7+i*3,11,paint);break;
            case "antenna":
                c.drawRoundRect(3,8,21,20,2,2,paint);path(c,7,2,12,7,17,2);path(c,9,23,15,23);break;
            case "av":
                c.drawCircle(5,10,2.5f,paint);c.drawCircle(12,10,2.5f,paint);c.drawCircle(19,10,2.5f,paint);
                path(c,5,13,5,20);path(c,12,13,12,20);path(c,19,13,19,20);break;
            case "wifi":
                curve(c,3,9,12,1,21,9);curve(c,6,12,12,6,18,12);curve(c,9,15,12,12,15,15);c.drawCircle(12,19,0.7f,paint);break;
            case "order":
                path(c,4,7,20,7);path(c,16,3,20,7,16,11);path(c,20,17,4,17);path(c,8,13,4,17,8,21);break;
            case "settings":
                c.drawCircle(12,12,6.3f,paint);c.drawCircle(12,12,2.4f,paint);
                for(int i=0;i<8;i++){int r=c.save();c.rotate(i*45,12,12);c.drawLine(12,2,12,5.2f,paint);c.restoreToCount(r);}break;
            case "screensaver":
                c.drawRoundRect(2.5f,4,21.5f,18,2,2,paint);path(c,7,14,10,11,13,14,16,10,20,14);c.drawCircle(7,8,1,paint);path(c,8,21,16,21);break;
            case "display":
                c.drawRoundRect(3,3,21,18,2,2,paint);path(c,8,21,16,21);path(c,12,18,12,21);c.drawCircle(12,10.5f,3,paint);break;
            case "sound_effects":
                path(c,9,17,9,5,20,3,20,15);
                path(c,9,9,20,7);c.drawOval(3,16,9,21,paint);c.drawOval(14,14,20,19,paint);break;
            case "sound":
                path(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);curve(c,16,8,20,12,16,16);curve(c,19,5,25,12,19,19);break;
            case "bluetooth":
                path(c,6,6,18,17,12,22,12,2,18,7,6,18);break;
        }
        c.restoreToCount(save);
    }
    private void path(Canvas c,float...xy){Path p=new Path();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);c.drawPath(p,paint);}
    private void curve(Canvas c,float x,float y,float cx,float cy,float ex,float ey){Path p=new Path();p.moveTo(x,y);p.quadTo(cx,cy,ex,ey);c.drawPath(p,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
