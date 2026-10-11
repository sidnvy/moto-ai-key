package dev.sid.motokeyprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/** Double AI-key target: capture/share outside ChatGPT, cancel active dictation inside.
 * Never taps Send; screenshot workflow is implemented by ContextualPress.
 */
public final class CancelActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    final ProbeService service = ProbeService.instance;
    finish(); // Return immediately to the previous foreground activity.
    if (service == null) {
      Log.w("MotoDualKey", "DOUBLE_IGNORED accessibility disconnected");
      return;
    }
    service.handler.postDelayed(() -> service.contextualPress.execute(), 180);
  }
}
