package dev.sid.motokeyprobe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Draws directly against the ACTUAL left screen edge, not inside a phone illustration.
 * Uses the owner-confirmed Moto Edge 70 position; no calibration UI.
 */
final class EdgeKeyHighlightView extends View {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  // Moto Edge 70 alignment confirmed by the owner on 2026-10-11.
  private final float fraction = .16390237f;
  private float pulse;
  private ValueAnimator animator;

  EdgeKeyHighlightView(Context context) {
    super(context);
    setContentDescription("左侧边缘高亮指向 Moto Edge 70 实体 AI Key。位置已固定。");
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
  }

  private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
  float markerY() { return getHeight() * fraction; }
  void startPulse() {
    if (animator != null) return;
    float enabled = 1f;
    try { enabled = Settings.Global.getFloat(getContext().getContentResolver(),
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f); } catch (Exception ignored) {}
    if (enabled == 0f) { pulse = 0.3f; invalidate(); return; }
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setRepeatCount(3);
    animator.setRepeatMode(ValueAnimator.REVERSE);
    animator.setDuration(900);
    animator.setInterpolator(new AccelerateDecelerateInterpolator());
    animator.addUpdateListener(a -> { pulse = (Float) a.getAnimatedValue(); invalidate(); });
    animator.start();
  }
  void playPress(boolean twice) {
    stopPulse();
    if (Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0) return;
    animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setRepeatMode(ValueAnimator.REVERSE);
    animator.setRepeatCount(twice ? 3 : 1);
    animator.setDuration(240);
    animator.addUpdateListener(a -> { pulse = (Float)a.getAnimatedValue(); invalidate(); });
    animator.start();
  }
  void stopPulse() {
    if (animator != null) { animator.cancel(); animator = null; }
    pulse = 0; invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float y = markerY();
    float radius = dp(74 + 8 * pulse);
    p.setStyle(Paint.Style.FILL);
    p.setShader(new RadialGradient(0, y, radius,
        new int[] {0x45F0A83D, 0x88FFF1D5, 0x50FFF1D5, 0x00FFF1D5},
        new float[] {0f, .30f, .68f, 1f}, Shader.TileMode.CLAMP));
    canvas.drawCircle(0, y, radius, p);
    p.setShader(null);
    p.setColor(0x36F0A83D);
    canvas.drawCircle(0, y, dp(35 + 7 * pulse), p);

    p.setColor(0xFFF0A83D);
    canvas.drawRoundRect(-dp(3), y-dp(36), dp(9), y+dp(36), dp(5), dp(5), p);
    p.setStrokeWidth(dp(2));
    // Short inward chevron points directly to the edge, without a remote leader.
    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2));
    p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    android.graphics.Path arrow = new android.graphics.Path();
    arrow.moveTo(dp(37), y-dp(7)); arrow.lineTo(dp(30),y); arrow.lineTo(dp(37),y+dp(7));
    canvas.drawPath(arrow,p);
    p.setColor(0x70F0A83D); p.setStrokeWidth(dp(1.5f));
    canvas.drawCircle(0,y,dp(29+14*pulse),p);
    p.setStyle(Paint.Style.FILL); p.setColor(0xff192438);
    p.setTextSize(dp(18)); p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
    canvas.drawText("AI Key",dp(53),y+dp(6),p);
  }

  @Override protected void onDetachedFromWindow() { stopPulse(); super.onDetachedFromWindow(); }
}
