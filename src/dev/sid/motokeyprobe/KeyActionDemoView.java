package dev.sid.motokeyprobe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.provider.Settings;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Local illustration: never launches, records, captures, or sends. */
final class KeyActionDemoView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ValueAnimator animator;
  private float progress = 1;
  private boolean twice, dictation;
  private Runnable finished;
  KeyActionDemoView(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
  private float d(float n) { return n * getResources().getDisplayMetrics().density; }
  void play(boolean doublePress, boolean inDictation, Runnable end) {
    stop(); twice = doublePress; dictation = inDictation; finished = end;
    if (Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0) {
      progress = 1; invalidate(); finished = null; end.run(); return;
    }
    progress = 0; animator = ValueAnimator.ofFloat(0,1);
    animator.setDuration(2200); animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a -> {
      progress = (Float)a.getAnimatedValue(); invalidate();
      if (progress >= 1 && finished != null) { Runnable done = finished; finished = null; done.run(); }
    }); animator.start();
  }
  void reset(boolean inDictation) { stop(); dictation = inDictation; twice = false; progress = 1; invalidate(); }
  void stop() { finished = null; if (animator != null) { animator.cancel(); animator = null; } }
  private void line(Canvas c,float x,float y,float x2,float y2,int color,float width) {
    paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(d(width)); paint.setStrokeCap(Paint.Cap.ROUND);
    c.drawLine(d(x),d(y),d(x2),d(y2),paint); paint.setStyle(Paint.Style.FILL);
  }
  private void text(Canvas c,String value,float x,float y,float size,int color) {
    paint.setColor(color);paint.setTextSize(d(size));paint.setTextAlign(Paint.Align.CENTER);c.drawText(value,d(x),d(y),paint);
  }
  private void box(Canvas c,float x,float y,float w,float h,int color,float radius) {
    paint.setStyle(Paint.Style.FILL);paint.setColor(color);
    c.drawRoundRect(d(x),d(y),d(x+w),d(y+h),d(radius),d(radius),paint);
  }
  private void focus(Canvas c,float x,float y,float w,float h) {
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2));paint.setColor(SetupActivity.BLUE);
    c.drawRoundRect(d(x),d(y),d(x+w),d(y+h),d(12),d(12),paint);paint.setStyle(Paint.Style.FILL);
  }
  private void waveform(Canvas c,float x,float y,float height) {
    for(int i=0;i<9;i++) {
      float h=7+height*(float)Math.abs(Math.sin(i*.8+progress*12));
      line(c,x+i*10,y-h/2,x+i*10,y+h/2,SetupActivity.BLUE,3);
    }
  }
  private void attachment(Canvas c) {
    box(c,22,125,48,39,SetupActivity.BLUE_TINT,6);
    paint.setStyle(Paint.Style.STROKE);paint.setColor(SetupActivity.BLUE);paint.setStrokeWidth(d(1.5f));
    c.drawCircle(d(56),d(136),d(3),paint);
    Path path=new Path();path.moveTo(d(28),d(155));path.lineTo(d(40),d(140));path.lineTo(d(48),d(148));path.lineTo(d(55),d(143));path.lineTo(d(65),d(157));c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
  }
  @Override protected void onDraw(Canvas c) {
    super.onDraw(c);c.save();
    float width=getWidth()/getResources().getDisplayMetrics().density;
    c.translate(d((width-340)/2f),0);
    // A fragment of the ChatGPT UI, without a phone frame or nested screen.
    box(c,0,0,340,245,SetupActivity.WHITE,0);
    line(c,16,20,30,20,SetupActivity.INK,1.5f);line(c,16,26,25,26,SetupActivity.INK,1.5f);
    text(c,"ChatGPT",170,27,17,SetupActivity.INK);
    text(c,"示意",310,27,11,SetupActivity.MUTED);
    line(c,12,43,328,43,SetupActivity.BORDER,1);
    box(c,217,57,106,27,SetupActivity.SOFT,12);
    line(c,229,71,307,71,SetupActivity.BORDER,3);
    line(c,18,101,191,101,SetupActivity.BORDER,3);
    line(c,18,115,158,115,SetupActivity.BORDER,3);
    float reveal=Math.max(0,Math.min(1,(progress-.4f)/.4f));
    boolean result=reveal>.05f;
    boolean voice=result&&!dictation&&!twice;
    boolean captured=result&&!dictation&&twice;
    boolean recording=(!result&&dictation)||captured;
    boolean completed=result&&dictation&&!twice;
    box(c,12,130,316,101,SetupActivity.SOFT,17);
    if(captured) {
      attachment(c);
      focus(c,17,120,58,49);
      text(c,"截图已附上",132,150,12,SetupActivity.BLUE);
    }
    if(recording) {
      waveform(c,126,193,10);
      line(c,34,186,47,199,SetupActivity.MUTED,2);line(c,47,186,34,199,SetupActivity.MUTED,2);
      box(c,287,180,27,27,SetupActivity.WHITE,13);
      box(c,295,188,11,11,SetupActivity.BLUE,2);
      focus(c,20,174,300,38);
    } else if(completed) {
      text(c,"帮我整理一下重点",123,164,14,SetupActivity.INK);
      line(c,48,181,219,181,SetupActivity.BLUE,2);
      focus(c,20,144,245,48);
    } else {
      text(c,"输入消息",76,161,14,SetupActivity.MUTED);
    }
    line(c,29,215,41,215,SetupActivity.MUTED,1.5f);line(c,35,209,35,221,SetupActivity.MUTED,1.5f);
    if(!recording) {
      // Send remains an inert illustration; the demo never sends anything.
      box(c,287,202,27,27,SetupActivity.BORDER,13);
      line(c,300,219,300,210,SetupActivity.MUTED,1.5f);
      line(c,296,214,300,210,SetupActivity.MUTED,1.5f);line(c,304,214,300,210,SetupActivity.MUTED,1.5f);
    }
    if(voice) {
      box(c,0,44,340,201,0xA6F7F8FC,0);
      c.save();c.translate(0,d((1-reveal)*20));
      box(c,42,77,256,117,SetupActivity.BLUE_TINT,20);
      waveform(c,130,120,22);
      text(c,"语音对话已开启",170,172,15,SetupActivity.BLUE);
      focus(c,42,77,256,117);c.restore();
    }
    if(result&&dictation&&twice) {
      text(c,"听写已取消",170,80,13,SetupActivity.BLUE);
      focus(c,20,142,240,34);
    }
    c.restore();
  }
  @Override protected void onDetachedFromWindow() { stop();super.onDetachedFromWindow(); }
}
