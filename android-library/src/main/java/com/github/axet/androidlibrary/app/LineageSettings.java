package com.github.axet.androidlibrary.app;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class LineageSettings {

    public static final Uri LINEAGEOS_SYSTEM = Uri.parse("content://lineagesettings/system");

    public static final String BERRY_DARK_OVERLAY = "berry_dark_overlay";
    public static final String OVERLAY_DARK = "org.lineageos.overlay.dark";
    public static final String OVERLAY_BLACK = "org.lineageos.overlay.black";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_VALUE = "value";

    public static class System {

        public static String getString(ContentResolver r, String name) {
            Cursor cursor = r.query(LINEAGEOS_SYSTEM, new String[]{COLUMN_VALUE}, COLUMN_NAME + "=?", new String[]{name}, null);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext())
                        return cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
                } catch (Exception e) {
                } finally {
                    cursor.close();
                }
            }
            return null;
        }

    }

}
