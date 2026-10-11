package dev.sid.motokeyprobe;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.SystemClock;
import android.net.Uri;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/** Motorola AI-key explicit activity target. Opens ChatGPT voice directly; retains contextual dictation actions. */
public class LaunchActivity extends Activity {
  private static long lastOutsideLaunch;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ProbeService service = ProbeService.instance;
    KeyguardManager lock = getSystemService(KeyguardManager.class);
    PowerManager power = getSystemService(PowerManager.class);
    if (!power.isInteractive() || lock.isKeyguardLocked()) {
      Log.i("MotoAssistKey", "Screen asleep or locked; no bypass attempted");
    } else {
      new Handler(Looper.getMainLooper()).postDelayed(() -> {
        if (service != null && service.handleSinglePress()) return;
        long now = SystemClock.elapsedRealtime();
        if (lastOutsideLaunch != 0 && now - lastOutsideLaunch < 2000) {
          Log.i("MotoAssistKey", "DUPLICATE_VOICE_LAUNCH_IGNORED");
          return;
        }
        lastOutsideLaunch = now;
        Intent voice = new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/voice"))
            .setPackage("com.openai.chatgpt");
        voice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
          startActivity(voice);
          Log.i("MotoAssistKey", "CHATGPT_DIRECT_VOICE_OPENED");
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
          Log.w("MotoAssistKey", "ChatGPT voice entry unavailable", error);
          Toast.makeText(this, "无法打开 ChatGPT 语音，请检查 ChatGPT 是否已安装并更新。", Toast.LENGTH_LONG).show();
          startActivity(new Intent(this, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
      }, 280);
    }
    finish();
  }
}
