package dev.sid.motokeyprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Figma AI Key onboarding, implemented using native Android Views.
 * Pure UI rewrite: LaunchActivity / CancelActivity / ProbeService remain unchanged.
 * The only visible launcher entry is this SetupActivity.
 */
public class SetupActivity extends Activity {
  static final int BG = 0xffF7F8FC, WHITE = 0xffffffff, INK = 0xff192438;
  static final int MUTED = 0xff66748B, BORDER = 0xffE4E8F0;
  static final int BLUE = 0xff4056CE, BLUE_TINT = 0xffE8ECFF;
  static final int AMBER = 0xffF0A83D, AMBER_TINT = 0xffFFF1D5;
  static final int GREEN = 0xff168369, GREEN_TINT = 0xffE3F5ED;
  static final int SOFT = 0xffF1F4FA;

  private FrameLayout root;
  private ScrollView scroll;
  private FrameLayout scene;
  private TextView primary;
  private EdgeKeyHighlightView edge;
  private KeyActionDemoView demo;
  private TextView demoResult, demoContext, demoSingle, demoDouble;
  private boolean demoDictation;
  private int page; // 0 find key, 1 permissions, 2 mappings, 3 try
  private boolean initialized;

  int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
  private static final String PREF = "aik_onboarding_v1";

  private GradientDrawable bg(int fill, int radiusDp) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill); d.setCornerRadius(dp(radiusDp)); return d;
  }
  private GradientDrawable bgWithBorder(int fill, int radiusDp) {
    GradientDrawable d = bg(fill, radiusDp);
    d.setStroke(dp(1), BORDER);
    return d;
  }
  private View frame(FrameLayout parent, String description, int x, int y,
                     int w, int h, int fill, int radius) {
    View v = new View(this);
    v.setBackground(bg(fill, radius));
    v.setContentDescription(description);
    place(parent, v, x, y, w, h);
    return v;
  }

  private void place(FrameLayout parent, View v, int x, int y, int w, int h) {
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    lp.leftMargin = dp(x); lp.topMargin = dp(y);
    parent.addView(v, lp);
  }

  private void fullWidth(FrameLayout parent, View child, int y, int h) {
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(h));
    lp.leftMargin = dp(24); lp.rightMargin = dp(24); lp.topMargin = dp(y);
    parent.addView(child, lp);
  }

  private TextView text(String value, float size, boolean bold, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextColor(color);
    t.setTextSize(size);
    t.setIncludeFontPadding(false);
    t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    t.setLineSpacing(dp(2), 1.05f);
    return t;
  }

  private TextView label(FrameLayout parent, String value, int x, int y,
                         int w, int h, float size, boolean bold, int color) {
    TextView t = text(value, size, bold, color);
    t.setGravity(Gravity.TOP | Gravity.START);
    place(parent, t, x, y, w, h);
    return t;
  }

  private void icon(FrameLayout parent, int drawable, int x, int y, int size) {
    ImageView v = new ImageView(this);
    v.setImageResource(drawable);
    v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    place(parent, v, x, y, size, size);
  }

  private void colorCircle(FrameLayout parent, int diameter, int y, int color) {
    View c = new View(this);
    c.setBackground(bg(color, diameter / 2));
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(diameter), dp(diameter),
        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    lp.topMargin = dp(y);
    parent.addView(c, lp);
  }

  private boolean open(Intent intent) {
    try { startActivity(intent); return true; }
    catch (android.content.ActivityNotFoundException | SecurityException ignored) { return false; }
  }
  private void notification(String message) {
    new AlertDialog.Builder(this)
        .setMessage(message).setPositiveButton("知道了", null).show();
  }

  boolean chatInstalled() {
    try {
      return getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt") != null;
    } catch (Exception ignored) { return false; }
  }
  boolean accessibilityEnabled() {
    try {
      String enabled = Settings.Secure.getString(getContentResolver(),
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
      if (enabled != null) {
        ComponentName component = new ComponentName(this, ProbeService.class);
        for (String item : enabled.split(":")) {
          ComponentName found = ComponentName.unflattenFromString(item);
          if (component.equals(found)) return true;
        }
      }
    } catch (Exception ignored) { }
    return ProbeService.instance != null;
  }
  private boolean mapping(Class<?> activity, String key) {
    try {
      String raw = Settings.System.getString(getContentResolver(), key);
      if (raw == null || !raw.startsWith("intent:")) return false;
      return new ComponentName(this, activity)
          .equals(Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).getComponent());
    } catch (Exception ignored) { return false; }
  }
  boolean mappedSingle() { return mapping(LaunchActivity.class, "tap_app_quick_single"); }
  boolean mappedDouble() {
    if (!mapping(CancelActivity.class, "tap_app_quick_double")) return false;
    try { return Settings.System.getInt(getContentResolver(), "double_press_enable", 0) == 1; }
    catch (Exception ignored) { return false; }
  }
  boolean fullyConfigured() {
    return chatInstalled() && accessibilityEnabled()
        && mappedSingle() && mappedDouble();
  }

  private void showChatGPT() {
    if (!chatInstalled()) {
      Intent store = new Intent(Intent.ACTION_VIEW,
          Uri.parse("market://details?id=com.openai.chatgpt"));
      if (open(store)) return;
      open(new Intent(Intent.ACTION_VIEW,
          Uri.parse("https://play.google.com/store/apps/details?id=com.openai.chatgpt")));
      return;
    }
    open(getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt"));
  }

  private void showAccessibilitySettings() {
    Intent specific = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
    specific.putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(this, ProbeService.class));
    if (!open(specific) && !open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
      notification("打开 Android 设置 → 辅助功能，启用 AI Key。");
    }
  }

  private void showMotorolaSettings() {
    Intent motorola = new Intent().setComponent(new ComponentName(
        "com.motorola.mykey", "com.motorola.mykey.activity.RedKeyActivity"));
    if (!open(motorola)) notification("未找到当前固件的 Motorola AI Key 设置页。请从系统设置手动查找。");
  }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    root = new FrameLayout(this);
    root.setBackgroundColor(BG);
    setContentView(root);
    // Android 15 enforces edge-to-edge: keep content clear of system bars.
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      root.setPadding(insets.getSystemWindowInsetLeft(),
          insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
          insets.getSystemWindowInsetBottom());
      return insets;
    });
    root.requestApplyInsets();

    scroll = new ScrollView(this) {
      @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
        return page == 0 ? false : super.onInterceptTouchEvent(e);
      }
      @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        return page == 0 ? false : super.onTouchEvent(e);
      }
      @Override public boolean onGenericMotionEvent(android.view.MotionEvent e) {
        return page == 0 ? false : super.onGenericMotionEvent(e);
      }
      @Override public boolean requestChildRectangleOnScreen(View child, android.graphics.Rect rect, boolean immediate) {
        return page == 0 ? false : super.requestChildRectangleOnScreen(child, rect, immediate);
      }
      @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (page == 0 && (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return false;
        return super.performAccessibilityAction(action, arguments);
      }
    };
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setClipToPadding(false);
    scroll.setPadding(0, 0, 0, dp(110));
    root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

    scene = new FrameLayout(this);
    scene.setMinimumHeight(dp(740));
    scroll.addView(scene, new ScrollView.LayoutParams(-1, -2));

    primary = text("继续", 17, true, WHITE);
    primary.setGravity(Gravity.CENTER);
    primary.setBackground(bg(BLUE, 18));
    primary.setClickable(true);
    primary.setFocusable(true);
    primary.setContentDescription("下一步");
    FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-1, dp(56),
        Gravity.BOTTOM);
    actionParams.leftMargin = dp(24);
    actionParams.rightMargin = dp(24);
    actionParams.bottomMargin = dp(25);
    root.addView(primary, actionParams);

    // Preview is non-destructive and affects the visible UI only; it does not
    // alter completion, Accessibility, Motorola mappings, or chat data.
    int previewStep = getIntent().getIntExtra("dev.sid.motokeyprobe.preview_step", -1);
    if (previewStep >= 0 && previewStep <= 3) page = previewStep == 3 ? 0 : previewStep;
    else page = state != null ? state.getInt("aik_page", 0)
        : 0;
    initialized = true;
    render(false);
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    int preview = intent.getIntExtra("dev.sid.motokeyprobe.preview_step", -1);
    if (preview >= 0 && preview <= 3) navigate(preview == 3 ? 0 : preview);
  }

  @Override protected void onSaveInstanceState(Bundle state) {
    state.putInt("aik_page", page); super.onSaveInstanceState(state);
  }

  @Override protected void onResume() {
    super.onResume();
    if (initialized) root.post(() -> { if (!isFinishing()) render(false); });
    if (edge != null) edge.startPulse();
  }

  @Override protected void onPause() {
    if (edge != null) edge.stopPulse();
    if (demo != null) { demo.stop(); demoResult.setText(""); }
    super.onPause();
  }

  @Override public void onBackPressed() {
    if (page > 0) { navigate(page - 1); return; }
    super.onBackPressed();
  }

  private void navigate(int next) {
    page = Math.min(2, Math.max(0, next));
    if (edge != null) { edge.stopPulse(); edge = null; }
    render(true);
    scroll.smoothScrollTo(0, 0);
  }

  private void render(boolean animate) {
    if (!initialized) return;
    if (edge != null) { edge.stopPulse(); edge = null; }
    if (demo != null) { demo.stop(); demo = null; }
    scene.removeAllViews();
    scroll.setOverScrollMode(page == 0 ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    if (page == 0) scroll.scrollTo(0, 0);
    buildHeader();
    if (page == 0) buildWelcome();
    if (page == 1) buildPermissions();
    if (page == 2) buildMapping();
    if (animate) {
      scene.setAlpha(.2f);
      scene.animate().alpha(1f).setDuration(200).start();
    } else scene.setAlpha(1f);
  }

  private void buildHeader() {
    ImageView symbol = new ImageView(this);
    symbol.setImageResource(R.drawable.ic_launcher);
    symbol.setContentDescription("AI Key");
    place(scene, symbol, 24, 30, 33, 33);
    label(scene, "AI Key", 67, 36, 155, 25, 15, true, INK);
    TextView stepText = text(
        page == 0 ? "设置" : page == 1 ? "授权" : "按键",
        12, false, MUTED);
    stepText.setGravity(Gravity.END);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(95), dp(26),
        Gravity.RIGHT | Gravity.TOP);
    sp.rightMargin = dp(24); sp.topMargin = dp(37);
    scene.addView(stepText, sp);
    if (page == 0) {
      stepText.setTextColor(BLUE);
      stepText.setOnClickListener(v -> navigate(1));
      stepText.setFocusable(true);
      stepText.setClickable(true);
    }
  }

  private void title(String heading, String subheading) {
    label(scene, heading, 24, 86, 345, 54, 31, true, INK);
    label(scene, subheading, 24, 146, 350, 50, 14, false, MUTED);
  }

  private void gestureCard(int y, String press, String heading, String detail) {
    FrameLayout card = new FrameLayout(this);
    card.setBackground(bgWithBorder(WHITE, 20));
    fullWidth(scene, card, y, 88);
    TextView count = text(press, 17, true, BLUE);
    count.setGravity(Gravity.CENTER);
    count.setBackground(bg(BLUE_TINT, 15));
    place(card, count, 18, 21, 49, 44);
    label(card, heading, 80, 19, 255, 28, 16, true, INK);
    label(card, detail, 80, 47, 265, 33, 13, false, MUTED);
  }

  private void buildWelcome() {
    edge = new EdgeKeyHighlightView(this);
    scene.addView(edge, new FrameLayout.LayoutParams(-1, dp(740)));
    // The actual screen edge is the location guide; outcomes appear on this page.
    demoContext = label(scene, "", 24, 90, 340, 25, 13, false, MUTED);
    demo = new KeyActionDemoView(this);
    FrameLayout.LayoutParams dv = new FrameLayout.LayoutParams(-1, dp(245));
    dv.topMargin = dp(270); scene.addView(demo, dv);
    demoResult = label(scene, "", 24, 523, 350, 38, 15, true, BLUE);
    demoResult.setGravity(Gravity.CENTER);
    demoResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

    TextView outside = text("其他应用中", 13, true, BLUE);
    TextView recording = text("ChatGPT 听写中", 13, true, MUTED);
    FrameLayout tabs = new FrameLayout(this);
    fullWidth(scene, tabs, 205, 48);
    FrameLayout.LayoutParams leftTab = new FrameLayout.LayoutParams(-1, -1);
    leftTab.width = (getResources().getDisplayMetrics().widthPixels - dp(56)) / 2;
    tabs.addView(outside, leftTab);
    FrameLayout.LayoutParams rightTab = new FrameLayout.LayoutParams(leftTab.width, -1, Gravity.RIGHT);
    tabs.addView(recording, rightTab);
    outside.setGravity(Gravity.CENTER); recording.setGravity(Gravity.CENTER);
    outside.setBackground(bg(WHITE, 12)); recording.setBackground(bg(SOFT, 12));
    outside.setSelected(true);
    outside.setOnClickListener(v -> selectDemoContext(false, outside, recording));
    recording.setOnClickListener(v -> selectDemoContext(true, outside, recording));
    outside.setFocusable(true); recording.setFocusable(true);

    FrameLayout buttons = new FrameLayout(this);
    fullWidth(scene, buttons, 572, 52);
    demoSingle = text("单击", 15, true, WHITE);
    demoDouble = text("双击", 15, true, BLUE);
    demoSingle.setGravity(Gravity.CENTER); demoDouble.setGravity(Gravity.CENTER);
    demoSingle.setBackground(bg(BLUE, 12)); demoDouble.setBackground(bgWithBorder(WHITE, 12));
    FrameLayout.LayoutParams singleLp = new FrameLayout.LayoutParams(leftTab.width, -1);
    buttons.addView(demoSingle, singleLp);
    buttons.addView(demoDouble, new FrameLayout.LayoutParams(leftTab.width, -1, Gravity.RIGHT));
    demoSingle.setFocusable(true); demoDouble.setFocusable(true);
    demoSingle.setOnClickListener(v -> playDemo(false));
    demoDouble.setOnClickListener(v -> playDemo(true));
    demoDictation = false;
    scene.post(() -> { if (demo != null && page == 0) playDemo(false); });
    frameBand(scene, "演示 · 不会录音、截图或发送", 648, SOFT);
    setPrimary(fullyConfigured() ? "开始使用" : "去设置", this::continueSetup);
  }

  private void selectDemoContext(boolean dictation, TextView outside, TextView recording) {
    demoDictation = dictation;
    outside.setSelected(!dictation); recording.setSelected(dictation);
    outside.setTextColor(dictation ? MUTED : BLUE);
    recording.setTextColor(dictation ? BLUE : MUTED);
    outside.setBackground(bg(dictation ? SOFT : WHITE, 12));
    recording.setBackground(bg(dictation ? WHITE : SOFT, 12));
    demoContext.setText("");
    demo.reset(dictation);
    demoResult.setText("");
  }

  private void playDemo(boolean twice) {
    final boolean dictation = demoDictation;
    demoResult.setText(twice ? "按下两次…" : "按下一次…");
    if (edge != null) edge.playPress(twice);
    demo.play(twice, dictation, () -> {
      demoResult.setText(dictation
          ? (twice ? "取消本次听写，不发送" : "完成听写，文字保留待编辑")
          : (twice ? "截图带入 ChatGPT，开始听写提问" : "打开 ChatGPT 语音对话"));
    });
  }

  private void frameBand(FrameLayout parent, String message, int y, int fill) {
    FrameLayout bar = new FrameLayout(this);
    bar.setBackground(bg(fill, 13));
    fullWidth(parent, bar, y, 30);
    label(bar, message, 20, 8, 320, 20, 12, false, MUTED);
  }

  private FrameLayout statusCard(int y, String heading, String desc, boolean active,
                                  String inactiveAction, Runnable onClick) {
    FrameLayout card = new FrameLayout(this);
    card.setBackground(bgWithBorder(WHITE, 19));
    fullWidth(scene, card, y, 104);
    label(card, heading, 18, 23, 213, 30, 17, true, INK);
    label(card, desc, 18, 57, 225, 35, 13, false, MUTED);

    TextView status = text(active ? "✓ 已设置" : inactiveAction, 13, true, active ? GREEN : INK);
    status.setGravity(Gravity.CENTER);
    status.setBackground(bg(active ? GREEN_TINT : AMBER_TINT, 12));
    FrameLayout.LayoutParams tag = new FrameLayout.LayoutParams(dp(93), dp(36),
        Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    tag.rightMargin = dp(17);
    card.addView(status, tag);
    card.setFocusable(true);
    card.setClickable(true);
    card.setContentDescription(heading + "，" + (active ? "已配置" : inactiveAction)
        + "。点按打开相应设置。");
    card.setOnClickListener(v -> onClick.run());
    return card;
  }

  private void buildPermissions() {
    boolean installed = chatInstalled(), access = accessibilityEnabled();
    if (!installed) {
      title("先安装 ChatGPT。", "语音由 ChatGPT 提供。");
      statusCard(240, "ChatGPT", "安装后回到这里", false, "安装", this::showChatGPT);
      setPrimary("安装 ChatGPT", this::showChatGPT);
    } else {
      title("允许按键帮你操作。", "截图提问、开始和完成听写需要辅助功能。");
      statusCard(240, "AI Key 辅助功能", access ? "已开启" : "在系统设置中开启 AI Key",
          access, "去开启", this::showAccessibilitySettings);
      setPrimary(access ? "继续" : "开启辅助功能", access ? this::continueSetup : this::showAccessibilitySettings);
    }
    frameBand(scene, "不会自动发送消息", 490, SOFT);
  }

  private void buildMapping() {
    title("设置按键动作。", "在 Motorola 设置里分配单击与双击。");
    boolean one = mappedSingle(), two = mappedDouble();
    statusCard(205, "单击", "语音对话 / 开始或完成听写", one, "去设置",
        this::showMotorolaSettings);
    statusCard(324, "双击", "截图提问 / 取消听写", two, "待确认",
        this::showMotorolaSettings);
    frameBand(scene, "在系统设置中，将两项动作分配给 AI Key", 490, SOFT);
    if (one && two) setPrimary("开始使用", this::continueSetup);
    else setPrimary("设置按键", this::showMotorolaSettings);
  }

  private void continueSetup() {
    if (!chatInstalled() || !accessibilityEnabled()) { navigate(1); return; }
    if (!mappedSingle() || !mappedDouble()) { navigate(2); return; }
    getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("complete", true).apply();
    open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    finish();
  }

  private void setPrimary(String caption, Runnable action) {
    primary.setText(caption);
    primary.setContentDescription(caption);
    primary.setOnClickListener(v -> action.run());
  }
}
