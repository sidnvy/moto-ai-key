package dev.sid.motokeyprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Front-view Edge 70 schematic. Highlighted AI key is on the left edge. */
public final class PhoneKeyGuideView extends View {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  public PhoneKeyGuideView(Context c) { super(c); setContentDescription("Motorola Edge 70 viewed from the front. AI key is on the left edge near the upper part of the phone. Volume and power buttons are on the opposite right edge."); }
  private void fill(Canvas c, int color, RectF r, float radius) {
    p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(r, radius, radius, p);
  }
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth(), h = getHeight();
    float scale = Math.min(w / 340f, h / 310f);
    canvas.save(); canvas.translate((w - 340 * scale) / 2f, (h - 310 * scale)/2f); canvas.scale(scale, scale);
    fill(canvas, Color.rgb(33, 43, 67), new RectF(112, 9, 231, 297), 22);
    fill(canvas, Color.rgb(240, 245, 251), new RectF(118, 15, 225, 290), 18);
    fill(canvas, Color.rgb(204, 210, 223), new RectF(160, 20, 182, 26), 5);
    // Screen content is a generic illustration, not a real screenshot.
    fill(canvas, Color.rgb(222, 231, 246), new RectF(132, 52, 210, 100), 9);
    fill(canvas, Color.rgb(225, 231, 237), new RectF(132, 112, 202, 119), 3);
    fill(canvas, Color.rgb(225, 231, 237), new RectF(132, 126, 192, 133), 3);
    fill(canvas, Color.rgb(225, 231, 237), new RectF(132, 140, 205, 147), 3);
    fill(canvas, Color.rgb(67, 105, 186), new RectF(165, 269, 182, 273), 2);
    // Left AI key, bright contrast
    fill(canvas, Color.rgb(244, 180, 42), new RectF(105, 70, 114, 125), 4);
    fill(canvas, Color.rgb(128, 140, 162), new RectF(230, 77, 236, 107), 3);
    fill(canvas, Color.rgb(128, 140, 162), new RectF(230, 125, 236, 146), 3);
    p.setColor(Color.rgb(244, 180, 42)); p.setStrokeWidth(2.5f);
    canvas.drawLine(106, 96, 47, 96, p);
    p.setColor(Color.rgb(35, 47, 71)); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    p.setTextSize(18); canvas.drawText("AI KEY", 18, 85, p);
    p.setTypeface(android.graphics.Typeface.DEFAULT);
    p.setTextSize(13); canvas.drawText("Left edge", 18, 119, p);
    p.setColor(Color.rgb(105, 115, 135)); p.setTextSize(12);
    canvas.drawText("Volume", 245, 96, p); canvas.drawText("Power", 245, 140, p);
    canvas.restore();
  }
}
