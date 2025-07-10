package com.androjoystick.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final Paint paint, firePaint, jumpPaint;
    private Thread thread;
    private boolean running;
    private float baseX, baseY, knobX, knobY, baseRadius;
    private boolean showFire, showJump;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        paint = new Paint();
        firePaint = new Paint();
        firePaint.setColor(Color.RED);
        firePaint.setTextSize(60);
        jumpPaint = new Paint();
        jumpPaint.setColor(Color.YELLOW);
        jumpPaint.setTextSize(60);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        baseX = getWidth() / 2f;
        baseY = getHeight() / 2f;
        baseRadius = Math.min(getWidth(), getHeight()) / 4f;
        knobX = baseX;
        knobY = baseY;
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
    }

    @Override
    public void run() {
        SurfaceHolder holder = getHolder();
        while (running) {
            if (!holder.getSurface().isValid()) continue;
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.BLACK);

            paint.setColor(Color.GRAY);
            canvas.drawCircle(baseX, baseY, baseRadius, paint);

            paint.setColor(Color.CYAN);
            canvas.drawCircle(knobX, knobY, baseRadius / 4f, paint);

            if (showFire) canvas.drawText("FIRE!", baseX - 60, baseY - baseRadius - 30, firePaint);
            if (showJump) canvas.drawText("JUMP!", baseX - 60, baseY + baseRadius + 60, jumpPaint);

            holder.unlockCanvasAndPost(canvas);
            try {
                Thread.sleep(16);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void updateJoystickPosition(float x, float y) {
        knobX = baseX + x * baseRadius;
        knobY = baseY + y * baseRadius;
    }

    public void showFireEffect() {
        showFire = true;
        new Handler().postDelayed(() -> showFire = false, 500);
    }

    public void showJumpEffect() {
        showJump = true;
        new Handler().postDelayed(() -> showJump = false, 500);
    }
}
