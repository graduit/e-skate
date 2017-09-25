package com.jhp.electricskateboard.helperclasses;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Created by J Park on 2/02/2017.
 */

public class TextToSpeechHelper {

    static TextToSpeech textToSpeech;
    static boolean isTtsOnInitListenerInitialized = false;

    static String mText;

    static TextToSpeech.OnInitListener ttsOnInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                int languageTtsAvailable = textToSpeech.setLanguage(Locale.US);

                isTtsOnInitListenerInitialized = true;

                if (mText != null && mText != "") {
                    textToSpeech.speak(mText, TextToSpeech.QUEUE_FLUSH, null, "ttsUniqueUtteranceId");
                }
            }
        }
    };


    /**
     *
     * @param context
     * @param text
     */
    public static void onResumeSlashConvertTextToSpeech(final Context context, final String text) {
        textToSpeech = new TextToSpeech(context, ttsOnInitListener);
        mText = text;
    }


    /**
     *
     * @param context
     * @param text
     */
    public static void convertTextToSpeech(final Context context, final String text) {
        if (isTtsOnInitListenerInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ttsUniqueUtteranceId");
        }
    }


    public static void onPauseTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        isTtsOnInitListenerInitialized = false;
    }
}
