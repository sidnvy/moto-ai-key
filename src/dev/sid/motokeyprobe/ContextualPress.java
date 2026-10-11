package dev.sid.motokeyprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import java.security.MessageDigest;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Double press context router.
 * ChatGPT composer visible: toggle Dictation/Stop (never Send).
 * Another unlocked app: screenshot its window, share as an unsent attachment
 * into ChatGPT's new share composer, then trigger Dictation.
 */
final class ContextualPress {
  private final ProbeService service;
  private boolean capturing;
  private int captureToken;
  private boolean waitingForStart;
  private boolean finishQueued;
  private int workflowToken;
  private long captureSerial = 0;

  boolean pendingStart() { return capturing || waitingForStart; }
  void queueFinish() { finishQueued = true; }
  void startFailed() {
    waitingForStart = false;
    finishQueued = false;
    Log.i("MotoMatrix", "PENDING_START_EXPIRED");
  }
  void recordingStarted() {
    waitingForStart = false;
    if (finishQueued) {
      finishQueued = false;
      service.handler.postDelayed(() -> service.handleSinglePress(), 120);
    }
  }
  private void cancelPending() {
    captureToken++;
    workflowToken++;
    capturing = false;
    waitingForStart = false;
    finishQueued = false;
    service.handler.removeCallbacks(service.worker);
    service.phase = -1;
    Log.i("MotoMatrix", "DOUBLE_CANCEL_PENDING");
  }

  ContextualPress(ProbeService service) { this.service = service; }

  void execute() {
    AccessibilityNodeInfo initial = service.getRootInActiveWindow();
    if (initial != null && "com.openai.chatgpt".contentEquals(initial.getPackageName())
        && service.find(initial, "Cancel Recording") != null) {
      cancelPending();
      service.cancel();
      Log.i("MotoMatrix", "DOUBLE_CANCEL_RECORDING_PRIORITY");
      return;
    }
    if (pendingStart() || (service.phase >= 0 && service.phase != 4 && service.phase != 5)) {
      cancelPending();
      return;
    }

    PowerManager power = service.getSystemService(PowerManager.class);
    KeyguardManager keyguard = service.getSystemService(KeyguardManager.class);
    if (power == null || !power.isInteractive() || (keyguard != null && keyguard.isKeyguardLocked())) {
      Log.i("MotoContext", "DOUBLE_IGNORED_LOCKED_OR_SLEEPING");
      return;
    }

    AccessibilityNodeInfo root = service.getRootInActiveWindow();
    String pkg = root != null && root.getPackageName() != null
        ? root.getPackageName().toString() : "";

    if ("com.openai.chatgpt".equals(pkg)) {
      if (service.find(root, "Stop recording") != null || service.find(root, "Cancel Recording") != null) {
        Log.i("MotoMatrix", "DOUBLE_CANCEL_RECORDING");
        service.cancel();
      } else Log.i("MotoMatrix", "DOUBLE_NOOP_IDLE_CHATGPT");
      return;
    }

    if (root == null || root.getWindowId() < 0 || pkg.length() == 0 ||
        pkg.equals(service.getPackageName()) || pkg.equals("com.android.systemui") ||
        pkg.equals("com.motorola.mykey")) {
      Log.i("MotoContext", "DOUBLE_IGNORED_UNSAFE_FOREGROUND=" + pkg);
      return;
    }

    final int token = ++captureToken;
    capturing = true;
    Log.i("MotoContext", "CAPTURE_REQUEST_SOURCE=" + pkg);
    try {
      service.takeScreenshotOfWindow(root.getWindowId(), service.getMainExecutor(), new TakeScreenshotCallback() {
        @Override public void onSuccess(ScreenshotResult result) {
          if (token != captureToken || !capturing) return;
          try {
            saveAndShare(result);
          } catch (Exception ex) {
            Log.w("MotoContext", "SCREENSHOT_SHARE_FAILED " + ex.getClass().getSimpleName());
            Toast.makeText(service, "Couldn't share this screenshot", Toast.LENGTH_SHORT).show();
          } finally {
            capturing = false;
          }
        }
        @Override public void onFailure(int code) {
          if (token != captureToken) return;
          capturing = false;
          Log.w("MotoContext", "SCREENSHOT_DENIED_OR_FAILED=" + code);
          Toast.makeText(service, "Screenshot unavailable in this app", Toast.LENGTH_SHORT).show();
        }
      });
    } catch (RuntimeException ex) {
      capturing = false;
      Log.w("MotoContext", "SCREENSHOT_API_FAILED", ex);
    }
  }

  private static String sha256(File file) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
        byte[] data = new byte[8192]; int len;
        while ((len = in.read(data)) != -1) md.update(data, 0, len);
      }
      StringBuilder out = new StringBuilder();
      for (byte b : md.digest()) out.append(String.format("%02x", b & 0xff));
      return out.toString();
    } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
  }
  private void saveAndShare(ScreenshotResult result) throws IOException {
    HardwareBuffer buffer = result.getHardwareBuffer();
    if (buffer == null) throw new IOException("No screenshot buffer");
    Bitmap bitmap = null;
    Bitmap copy = null;
    try {
      ColorSpace space = result.getColorSpace();
      if (space == null) space = ColorSpace.get(ColorSpace.Named.SRGB);
      bitmap = Bitmap.wrapHardwareBuffer(buffer, space);
      if (bitmap == null) throw new IOException("Cannot wrap buffer");
      copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
      if (copy == null) throw new IOException("Cannot copy pixels");
      // Retire old private captures after two days. Do not delete the current
      // attachment: ChatGPT may still hold its granted URI while composing.
      File[] oldCaptures = service.getCacheDir().listFiles();
      if (oldCaptures != null) for (File old : oldCaptures) {
        if (old.getName().matches("capture-[0-9]+-[0-9]+\\.png")
            && System.currentTimeMillis() - old.lastModified() > 172800000L) old.delete();
      }
      String name = "capture-" + SystemClock.elapsedRealtimeNanos() + "-" + (++captureSerial) + ".png";
      File png = new File(service.getCacheDir(), name);
      try (FileOutputStream stream = new FileOutputStream(png)) {
        if (!copy.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("PNG encode failed");
      }
      Uri freshUri = SnapshotProvider.uriFor(name);
      String fingerprint = sha256(png);
      Intent share = new Intent(Intent.ACTION_SEND);
      share.setPackage("com.openai.chatgpt");
      share.setType("image/png");
      share.putExtra(Intent.EXTRA_STREAM, freshUri);
      share.setClipData(ClipData.newUri(service.getContentResolver(), name, freshUri));
      share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
      // User explicitly initiated screenshot+share with a physical double press.
      service.startActivity(share);
      Log.i("MotoContext", "SCREENSHOT_STAGED_IN_CHATGPT capture=" + name + " sha256=" + fingerprint + " bytes=" + png.length());

      // Wait for ChatGPT's new share composer. The existing service clicks
      // Dictation only (and later Stop), never Send Message.
      waitingForStart = true;
      final int expected = ++workflowToken;
      service.handler.postDelayed(() -> {
        if (expected == workflowToken && waitingForStart) service.begin(true);
      }, 650);
    } finally {
      if (copy != null) copy.recycle();
      if (bitmap != null) bitmap.recycle();
      buffer.close();
    }
  }
}
