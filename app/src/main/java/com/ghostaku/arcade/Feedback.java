package com.ghostaku.arcade;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import java.util.HashSet;
import java.util.Set;

final class Feedback {
  static final int SELECT = 0,
      DROP = 1,
      CLEAR = 2,
      BOUNCE = 3,
      EAT = 4,
      FLAG = 5,
      POWER = 6,
      FAIL = 7,
      WIN = 8;
  private final SoundPool pool;
  private final Set<Integer> loaded = new HashSet<>();
  private final int[] ids;
  private final Vibrator vibrator;
  private long lastHaptic;

  Feedback(Context context) {
    pool =
        new SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            .build();
    pool.setOnLoadCompleteListener(
        (p, id, status) -> {
          if (status == 0) loaded.add(id);
        });
    int[] sounds = {
      R.raw.sfx_select,
      R.raw.sfx_drop,
      R.raw.sfx_clear,
      R.raw.sfx_bounce,
      R.raw.sfx_eat,
      R.raw.sfx_flag,
      R.raw.sfx_power,
      R.raw.sfx_fail,
      R.raw.sfx_win
    };
    ids = new int[sounds.length];
    for (int i = 0; i < sounds.length; i++) ids[i] = pool.load(context, sounds[i], 1);
    vibrator =
        Build.VERSION.SDK_INT >= 31
            ? context.getSystemService(android.os.VibratorManager.class).getDefaultVibrator()
            : (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  void play(View v, int event, boolean sound, boolean haptic) {
    if (sound && loaded.contains(ids[event])) pool.play(ids[event], .62f, .62f, 1, 0, 1f);
    long now = android.os.SystemClock.uptimeMillis();
    if (!haptic
        || vibrator == null
        || !vibrator.hasVibrator()
        || now - lastHaptic < 45
        || Settings.System.getInt(
                v.getContext().getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
            == 0) return;
    lastHaptic = now;
    if (event == SELECT || event == FLAG) {
      v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
      return;
    }
    if (Build.VERSION.SDK_INT >= 29) {
      int effect =
          event == FAIL
              ? VibrationEffect.EFFECT_HEAVY_CLICK
              : (event == WIN || event == CLEAR)
                  ? VibrationEffect.EFFECT_DOUBLE_CLICK
                  : event == BOUNCE ? VibrationEffect.EFFECT_TICK : VibrationEffect.EFFECT_CLICK;
      vibrator.vibrate(VibrationEffect.createPredefined(effect));
    } else
      v.performHapticFeedback(
          event == BOUNCE
              ? HapticFeedbackConstants.CLOCK_TICK
              : HapticFeedbackConstants.KEYBOARD_TAP);
  }

  void stop() {
    pool.autoPause();
    if (vibrator != null) vibrator.cancel();
  }

  void release() {
    pool.release();
  }
}
