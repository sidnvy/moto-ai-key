package dev.sid.motokeyprobe;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
public class ProbeService extends AccessibilityService {
  static ProbeService instance;
  final Handler handler = new Handler();
  final ContextualPress contextualPress = new ContextualPress(this);
  int phase = -1;
  long deadline;
  boolean keepCurrentChat;
  String lastForeground = "";
  final Runnable worker = new Runnable() { public void run() { step(); } };
  @Override protected void onServiceConnected() {
    instance = this;
    Log.i("MotoKeyProbe", "AUTOMATION_CONNECTED");
  }
  public boolean isChatForeground() {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root != null && root.getPackageName() != null) {
      String pkg = root.getPackageName().toString();
      if (!pkg.equals(getPackageName())) return pkg.equals("com.openai.chatgpt");
    }
    return lastForeground.equals("com.openai.chatgpt");
  }
  /** Main-key priority: active recording > pending start > ChatGPT composer > outside direct voice. */
  public boolean handleSinglePress() {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    String pkg = root != null && root.getPackageName() != null ? root.getPackageName().toString() : "";
    if ("com.openai.chatgpt".equals(pkg)) {
      if (find(root, "Stop recording") != null) {
        handler.removeCallbacks(worker);
        phase = -1;
        boolean stopped = click(root, "Stop recording");
        Log.i("MotoMatrix", "SINGLE_FINISH=" + stopped);
        return true;
      }
      if (contextualPress.pendingStart() || (phase >= 0 && phase != 4 && phase != 5)) {
        contextualPress.queueFinish();
        Log.i("MotoMatrix", "SINGLE_FINISH_QUEUED");
        return true;
      }
      if (find(root, "Dictation") != null) {
        Log.i("MotoMatrix", "SINGLE_START_CURRENT_CHAT");
        begin(true);
        return true;
      }
      Log.i("MotoMatrix", "SINGLE_NOOP_CHATGPT_NONCOMPOSER");
      return true;
    }
    if (contextualPress.pendingStart()) {
      contextualPress.queueFinish();
      return true;
    }
    Log.i("MotoMatrix", "SINGLE_DIRECT_VOICE_OUTSIDE_CHATGPT");
    return false;
  }
  public void begin(boolean keepCurrent) {
    handler.removeCallbacks(worker);
    keepCurrentChat = keepCurrent;
    phase = 0;
    deadline = SystemClock.uptimeMillis() + 12000;
    handler.postDelayed(worker, 50);
    Log.i("MotoKeyProbe", "START_REQUEST mode=" + (keepCurrent ? "CURRENT_CHAT" : "NEW_CHAT"));
  }
  /** Called after hidden double-press activity has finished. Does nothing outside a ChatGPT composer. */
  public void toggleCurrentChatDictation() {
    AccessibilityNodeInfo root = getRootInActiveWindow();
    String pkg = root != null && root.getPackageName() != null
        ? root.getPackageName().toString() : "";
    if (!"com.openai.chatgpt".equals(pkg)) {
      Log.i("MotoDualKey", "DOUBLE_IGNORED foreground-not-chatgpt package=" + pkg);
      return;
    }
    // Voice-assistant overlay is also in the ChatGPT package. Require the
    // actual conversation composer controls instead of accepting its package alone.
    boolean canStart = find(root, "Dictation") != null;
    boolean canStop = find(root, "Stop recording") != null;
    if (!canStart && !canStop) {
      Log.i("MotoDualKey", "DOUBLE_IGNORED no-dictation-controls");
      return;
    }
    Log.i("MotoDualKey", "DOUBLE_TOGGLE_CURRENT_CHAT recording=" + canStop);
    begin(true);
  }
  public void cancel() {
    handler.removeCallbacks(worker);
    phase = 4;
    deadline = SystemClock.uptimeMillis() + 2500;
    handler.postDelayed(worker, 30);
    Log.i("MotoKeyProbe", "CANCEL_REQUEST pending start cleared");
  }
  AccessibilityNodeInfo find(AccessibilityNodeInfo n, String description) {
    if (n == null) return null;
    if (description.contentEquals(n.getContentDescription() == null ? "" : n.getContentDescription())) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo result = find(n.getChild(i), description);
      if (result != null) return result;
    }
    return null;
  }
  boolean click(AccessibilityNodeInfo root, String name) {
    AccessibilityNodeInfo n = find(root, name);
    for (int i = 0; n != null && i < 5; i++, n = n.getParent()) {
      if (n.isClickable() && n.isEnabled() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        Log.i("MotoKeyProbe", "CLICK " + name);
        return true;
      }
    }
    return false;
  }
  void step() {
    if (phase < 0) return;
    if (SystemClock.uptimeMillis() > deadline) {
      Log.i("MotoKeyProbe", phase == 4 ? "CANCEL_NO_RECORDING" : "TIMEOUT phase=" + phase);
      phase = -1;
      contextualPress.startFailed();
      return;
    }
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root != null && "com.openai.chatgpt".contentEquals(root.getPackageName())) {
      if (phase == 4) {
        if (click(root, "Cancel Recording")) phase = 5;
      } else if (phase == 5) {
        if (find(root, "Cancel Recording") == null && find(root, "Stop recording") == null) {
          Log.i("MotoKeyProbe", "VERIFIED_CANCELLED"); phase = -1; return;
        }
      } else if (phase == 0) {
        if (find(root, "Stop recording") != null) {
          if (click(root, "Stop recording")) { Log.i("MotoKeyProbe", "STOPPED"); phase = -1; return; }
        } else if (keepCurrentChat) {
          if (click(root, "Dictation")) phase = 3;
        } else if (click(root, "New chat")) phase = 2;
        else if (click(root, "Menu")) phase = 1;
      } else if (phase == 1 && click(root, "New chat")) phase = 2;
      else if (phase == 2 && click(root, "Dictation")) phase = 3;
      else if (phase == 3 && find(root, "Stop recording") != null) {
        Log.i("MotoKeyProbe", "VERIFIED_RECORDING"); phase = -1;
        contextualPress.recordingStarted(); return;
      }
    }
    handler.postDelayed(worker, 100);
  }
  @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      AccessibilityNodeInfo root = getRootInActiveWindow();
      if (root != null && root.getPackageName() != null) {
        String pkg = root.getPackageName().toString();
        if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.systemui")
            && !pkg.equals("com.google.android.inputmethod.latin")) lastForeground = pkg;
      }
    }
    if (phase >= 0) {
      handler.removeCallbacks(worker);
      handler.postDelayed(worker, 20);
    }
  }
  @Override public void onInterrupt() { handler.removeCallbacks(worker); phase = -1; }
  @Override public void onDestroy() { instance = null; handler.removeCallbacks(worker); super.onDestroy(); }
}
