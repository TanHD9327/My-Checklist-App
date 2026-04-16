package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SaturationValueView extends View {
    private Paint paint;
    private Paint cursorPaint;
    private float hue = 0;
    private float saturation = 1f;
    private float value = 1f;
    private OnColorChangedListener listener;

    public interface OnColorChangedListener {
        void onColorChanged(float s, float v);
    }

    public SaturationValueView(Context context) {
        super(context);
        init();
    }

    public SaturationValueView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        cursorPaint = new Paint();
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(5);
        cursorPaint.setColor(Color.WHITE);
    }

    public void setHue(float hue) {
        this.hue = hue;
        invalidate();
    }

    public void setSaturationValue(float s, float v) {
        this.saturation = s;
        this.value = v;
        invalidate();
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        // Draw Saturation/Value gradient
        // Simplified: using two passes for gradients
        // 1. Horizontal: White to Hue color
        // 2. Vertical: Transparent to Black
        
        int mainColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        
        Shader satShader = new LinearGradient(0, 0, width, 0, Color.WHITE, mainColor, Shader.TileMode.CLAMP);
        paint.setShader(satShader);
        canvas.drawRect(0, 0, width, height, paint);
        
        Shader valShader = new LinearGradient(0, 0, 0, height, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        paint.setShader(valShader);
        canvas.drawRect(0, 0, width, height, paint);

        // Draw cursor
        float cx = saturation * width;
        float cy = (1f - value) * height;
        cursorPaint.setColor(value > 0.5f ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(cx, cy, 15, cursorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getX();
            float y = event.getY();
            
            saturation = Math.max(0, Math.min(x / getWidth(), 1f));
            value = Math.max(0, Math.min(1f - (y / getHeight()), 1f));
            
            if (listener != null) {
                listener.onColorChanged(saturation, value);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
