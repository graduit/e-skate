package com.jhp.electricskateboard.helperclasses;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import java.util.Hashtable;

/**
 * Created by J Park on 2/02/2017.
 *
 * You should cache the TypeFace, otherwise you might risk memory leaks on older handsets.
 * Caching will increase speed as well since it's not super fast to read from assets all the time.
 */
public class CustomFontHelper {

    /**
     * Sets a font on a textview
     * @param textview
     * @param font i.e. the font resource path
     * @param context
     */
    public static void setCustomFont(TextView textview, String font, Context context) {
        if(font == null) {
            return;
        }
        Typeface tf = FontCache.get(font, context);
        if(tf != null) {
            textview.setTypeface(tf);
        }
    }


    public static class FontCache {
        private static Hashtable<String, Typeface> fontCache = new Hashtable<String, Typeface>();

        public static Typeface get(String name, Context context) {
            Typeface tf = fontCache.get(name);
            if(tf == null) {
                try {
                    tf = Typeface.createFromAsset(context.getAssets(), name);
                } catch (Exception e) {
                    return null;
                }
                fontCache.put(name, tf);
            }
            return tf;
        }
    }
}
