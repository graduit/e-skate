package com.jhp.electricskateboard.helperclasses;

import android.content.Context;
import android.media.MediaPlayer;

import com.jhp.electricskateboard.R;

/**
 * Created by J Park on 2/02/2017.
 */

public class IgnitionSoundHelper {
    /**
     * Play ignition sound when Android device connected to the Arduino device via bluetooth
     */
    public static void playIgnitionSound(Context context) {
        MediaPlayer ignitionSound = MediaPlayer.create(context, R.raw.lamborghini_ignition_sound);
        ignitionSound.setLooping(false);
        ignitionSound.start();
    }
}
