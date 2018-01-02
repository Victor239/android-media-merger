package com.github.axet.mover.widgets;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;

import com.github.axet.mover.app.Camera;
import com.github.axet.mover.app.Storage;

import java.util.Date;

public class NameFormatPreferenceCompat extends com.github.axet.androidlibrary.widgets.NameFormatPreferenceCompat {
    public NameFormatPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NameFormatPreferenceCompat(Context context) {
        super(context);
    }

    @Override
    public String getFormatted(String str) {
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        for (int i = 0; i < text.length; i++) {
            String t = text[i].toString();
            String v = values[i].toString();
            if (v.equals(str))
                return t;
        }
        return Camera.getFormatted(new Storage(getContext()), str, Uri.parse("file://Parent+Folder/IMG_2016010101"), new Date(1493561080000l)) + ".png";
    }
}
