package com.jhp.electricskateboard.helperclasses;

import android.content.Context;
import android.os.Vibrator;
import android.util.Log;

/**
 * Created by J Park on 2/02/2017.
 */

public class VibrationHelper {
    /**
     * Vibrate for a fixed amount of time (500ms in this case)
     * @param context
     */
    public static void vibrate(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(500);
    }


    /**
     * Vibrate for 100 milliseconds
     * @param context
     */
    public static void vibrateShort(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(100);
    }


    /**
     * Vibrate using a pattern (500ms in this case)
     * @param context
     */
    public static void vibratePattern(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Start without a delay
        // Each element then alternates between vibrate, sleep, vibrate, sleep...
        long[] pattern = {0, 100, 1000, 300, 200, 100, 500, 200, 100};

        v.vibrate(pattern, -1);
    }


    static Vibrator indefiniteVibrator;
    /**
     * Vibrate indefinitely (Start)
     * @param context
     */
    public static void vibrateIndefinitelyStart(Context context) {
        indefiniteVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Start without a delay, Vibrate for 100ms, Sleep for 1000 ms
        long[] pattern = {0, 100, 1000};

        // The '0' here means to repeat indefinitely
        indefiniteVibrator.vibrate(pattern, 0);
    }


    /**
     * Vibrate indefinitely (Stop)
     */
    public static void vibrateIndefinitelyStop() {
        indefiniteVibrator.cancel();
    }


    /**
     * Vibrate Troubleshooting
     * If your device won't vibrate, first make sure that it can vibrate.
     * Also, need to make sure that you've given your application the permission to vibrate!
     * i.e. <uses-permission android:name="android.permission.VIBRATE"/>
     * @param context
     */
    public static void vibrateTroubleshooting(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (v.hasVibrator()) {
            Log.v("Can Vibrate", "Yes");
        } else {
            Log.v("Can Vibrate", "No");
        }
    }
}
