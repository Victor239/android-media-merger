package com.github.axet.androidlibrary.preferences;

import android.content.Context;
import android.os.Build;
import androidx.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;

import com.github.axet.androidlibrary.app.Storage;

public class LegacyStoragePreferenceCompat extends SwitchPreferenceCompat {
    public LegacyStoragePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        onResume();
    }

    public LegacyStoragePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        onResume();
    }

    public LegacyStoragePreferenceCompat(Context context) {
        super(context);
        onResume();
    }

    @Override
    public boolean callChangeListener(Object newValue) {
        if (!super.callChangeListener(newValue))
            return false;
        Storage.showExternalStorageManager(getContext());
        return false;
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT >= 30 && getContext().getApplicationInfo().targetSdkVersion >= 30) {
            setVisible(true);
        } else {
            setVisible(false);
            return;
        }
        boolean b = Storage.isExternalStorageManager(getContext());
        setChecked(b);
    }
}
