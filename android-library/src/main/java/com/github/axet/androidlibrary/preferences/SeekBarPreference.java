package com.github.axet.androidlibrary.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.core.content.SharedPreferencesCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarPreference extends DialogPreference {
    public float value = 0;

    public static void show(Fragment f, String key) {
        SeekBarPreferenceDialogFragment d = SeekBarPreferenceDialogFragment.newInstance(key);
        d.setTargetFragment(f, 0);
        d.show(f.getFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
    }

    public static class SeekBarPreferenceDialogFragment extends PreferenceDialogFragmentCompat {
        public boolean mPreferenceChanged;

        public float value;
        public LinearLayout layout;
        public TextView valueText;
        public SeekBar seekBar;

        public SeekBarPreferenceDialogFragment() {
        }

        public static SeekBarPreferenceDialogFragment newInstance(String key) {
            SeekBarPreferenceDialogFragment fragment = new SeekBarPreferenceDialogFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) {
                value = savedInstanceState.getFloat("value");
                mPreferenceChanged = savedInstanceState.getBoolean("changed");
            } else {
                SeekBarPreference preference = (SeekBarPreference) getPreference();
                value = preference.getValue();
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putFloat("value", value);
            outState.putBoolean("changed", mPreferenceChanged);
        }

        int getThemeColor(int id) {
            TypedValue typedValue = new TypedValue();
            Context context = getActivity();
            Resources.Theme theme = context.getTheme();
            if (theme.resolveAttribute(id, typedValue, true)) {
                if (Build.VERSION.SDK_INT >= 23)
                    return context.getResources().getColor(typedValue.resourceId, theme);
                else
                    return context.getResources().getColor(typedValue.resourceId);
            } else {
                return Color.TRANSPARENT;
            }
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            Context context = builder.getContext();

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams lp;

            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            seekBar = new SeekBar(context);
            layout.addView(seekBar, lp);

            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            valueText = new TextView(context);
            updateText();
            valueText.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
            layout.addView(valueText, lp);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int newValue, boolean fromUser) {
                    if (!fromUser)
                        return;
                    mPreferenceChanged = true;
                    value = newValue / 100f;
                    updateText();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            seekBar.setKeyProgressIncrement(1);
            seekBar.setMax(100);
            seekBar.setProgress((int) (value * 100));

            builder.setView(layout);
        }

        public void updateText() {
            SeekBarPreference preference = (SeekBarPreference) getPreference();
            valueText.setText(preference.format(value));
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            SeekBarPreference preference = (SeekBarPreference) getPreference();
            if (positiveResult && this.mPreferenceChanged) {
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                }
            }
            this.mPreferenceChanged = false;
        }
    }

    /**
     * The SeekBarPreference constructor.
     *
     * @param context of this preference.
     * @param attrs   custom xml attributes.
     */
    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setValue(float f) {
        this.value = f;
        if (this.shouldPersist()) {
            SharedPreferences.Editor editor = this.getPreferenceManager().getSharedPreferences().edit();
            editor.putFloat(this.getKey(), value);
            SharedPreferencesCompat.EditorCompat.getInstance().apply(editor);
        }
    }

    public float getValue() {
        return value;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore existing state
            value = this.getPersistedFloat(1);
        } else {
            // Set default state from the XML attribute
            value = (Float) defaultValue;
            persistFloat(value);
        }
    }

    public void updateSummary() {
        setSummary(format(value));
    }

    public String format(float value) {
        return String.valueOf((int) (value * 100)) + " %";
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getFloat(index, 0);
    }
}
