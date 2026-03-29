package com.github.axet.androidlibrary.widgets;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

public class ThemeUtils {

    public static int sp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, dp, context.getResources().getDisplayMetrics());
    }

    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    // get colors.xml / some_name_statelist.xml
    public static int getColor(Context context, int id) {
        return ContextCompat.getColor(context, id);
    }

    // get attrs.xml / styles.xml
    public static int getThemeColor(Context context, int id) {
        TypedValue out = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(id, out, true);
        if (found) {
            switch (out.type) {
                case TypedValue.TYPE_INT_COLOR_ARGB4:
                case TypedValue.TYPE_INT_COLOR_ARGB8:
                case TypedValue.TYPE_INT_COLOR_RGB4:
                case TypedValue.TYPE_INT_COLOR_RGB8:
                    return out.data;
                default:
                    try {
                        return getColor(context, out.resourceId);
                    } catch (Resources.NotFoundException e) { // API16 crashes, since android.* colors not found
                        return 0;
                    }
            }
        } else {
            throw new Resources.NotFoundException("Color resource ID #0x" + Integer.toHexString(id));
        }
    }
}
