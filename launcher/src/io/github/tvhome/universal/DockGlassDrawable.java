package io.github.tvhome.universal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Lightweight glass styling, with neutral reflections independent of app artwork. */
final class DockGlassDrawable extends Drawable {
    private final float density;
    private final Paint surface=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reflection=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area=new RectF();
    private final Path clip=new Path(), sweep=new Path();
    private float radius;
    private boolean focused;
    private final Paint focusFill=new Paint(Paint.ANTI_ALIAS_FLAG);
    void setFocused(boolean focused){this.focused=focused;invalidateSelf();}
    DockGlassDrawable(float density){this.density=density;rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(density);inner.setStyle(Paint.Style.STROKE);inner.setStrokeWidth(density*0.7f);}
    @Override protected void onBoundsChange(Rect bounds){
        float inset=density;
        area.set(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset);
        if(area.width()<=0||area.height()<=0)return;
        radius=Math.min(28*density,area.height()/2f);
        surface.setShader(new LinearGradient(0,area.top,0,area.bottom,
            new int[]{0x36FFFFFF,0x16FFFFFF,0x0DFFFFFF,0x30232323},new float[]{0f,0.18f,0.6f,1f},Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(area.left,area.top,area.right,area.bottom,
            new int[]{0xA6FFFFFF,0x38FFFFFF,0x14FFFFFF,0x66FFFFFF},new float[]{0f,0.3f,0.65f,1f},Shader.TileMode.CLAMP));
        reflection.setShader(new LinearGradient(area.left,area.top,area.right,area.bottom,
            new int[]{0x22FFFFFF,0x08FFFFFF,0x00FFFFFF},new float[]{0f,0.35f,1f},Shader.TileMode.CLAMP));
        inner.setShader(new LinearGradient(0,area.top,0,area.bottom,new int[]{0x30FFFFFF,0x00FFFFFF,0x20FFFFFF},null,Shader.TileMode.CLAMP));
        clip.reset();clip.addRoundRect(area,radius,radius,Path.Direction.CW);
        sweep.reset();sweep.moveTo(area.left,area.top);sweep.lineTo(area.right,area.top);
        sweep.cubicTo(area.right-area.width()*0.12f,area.top+area.height()*0.23f,
            area.left+area.width()*0.25f,area.top+area.height()*0.06f,area.left,area.top+area.height()*0.44f);sweep.close();
    }
    /** Share the exact glass contour with the host's child clipping. */
    @Override public void getOutline(android.graphics.Outline outline){
        if(area.isEmpty()){outline.setEmpty();return;}
        outline.setRoundRect((int)Math.ceil(area.left),(int)Math.ceil(area.top),
                (int)Math.floor(area.right),(int)Math.floor(area.bottom),radius);
        outline.setAlpha(1f);
    }
    @Override public void draw(Canvas canvas){
        if(area.isEmpty())return;
        canvas.drawRoundRect(area,radius,radius,surface);
        if(focused){focusFill.setColor(0x30FFFFFF);canvas.drawRoundRect(area,radius,radius,focusFill);}
        int saved=canvas.save();canvas.clipPath(clip);canvas.drawPath(sweep,reflection);canvas.restoreToCount(saved);
        canvas.drawRoundRect(area,radius,radius,rim);
        RectF inside=new RectF(area);inside.inset(density*2,density*2);
        canvas.drawRoundRect(inside,Math.max(0,radius-density*2),Math.max(0,radius-density*2),inner);
    }
    @Override public void setAlpha(int alpha){surface.setAlpha(alpha);rim.setAlpha(alpha);reflection.setAlpha(alpha);inner.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){surface.setColorFilter(filter);rim.setColorFilter(filter);reflection.setColorFilter(filter);inner.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
