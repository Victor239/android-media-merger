package com.github.axet.androidlibrary.preferences;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class NameFormatPreferenceCompat extends ListPreference {

    public static void show(PreferenceFragmentCompat f, String key) {
        NameFormatDialogPreferenceCompat d = new NameFormatDialogPreferenceCompat();
        Bundle args = new Bundle();
        args.putString("key", key);
        d.setArguments(args);
        d.setTargetFragment(f, 0);
        d.show(f.getFragmentManager(), "");
    }

    public static class NameFormatDialogPreferenceCompat extends PreferenceDialogFragmentCompat {
        public String value;

        @Override
        protected View onCreateDialogView(Context context) {
            final NameFormatPreferenceCompat pref = (NameFormatPreferenceCompat) getPreference();

            LayoutInflater inflater = LayoutInflater.from(getContext());

            boolean def = false;

            ScrollView v = new ScrollView(getContext());
            LinearLayout ll = new LinearLayout(getContext());
            ll.setOrientation(LinearLayout.VERTICAL);

            final ArrayList<Checkable> all = new ArrayList<>();

            final LinearLayout lh = new LinearLayout(getContext());
            lh.setOrientation(LinearLayout.HORIZONTAL);
            final EditText edit = new EditText(getContext());
            final CheckedTextView check = (CheckedTextView) inflater.inflate(android.R.layout.simple_list_item_single_choice, lh, false);
            final TextView format = new TextView(getContext());
            format.setPadding(check.getPaddingLeft(), 0, check.getPaddingRight(), 0);
            lh.setPadding(check.getPaddingLeft(), 0, 0, 0);
            lh.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            lh.addView(check, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0));
            lh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (Checkable c : all) {
                        c.setChecked(false);
                    }
                    edit.setEnabled(true);
                    check.setChecked(true);
                    edit.requestFocus();
                }
            });
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lh.performClick();
                }
            });
            edit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    value = s.toString();
                    format.setText(pref.getFormatted(value));
                }
            });

            CharSequence[] text = pref.getEntries();
            CharSequence[] values = pref.getEntryValues();
            final String current = pref.getValue();
            for (int i = 0; i < text.length; i++) {
                CharSequence t = text[i];
                final String tt = values[i].toString();
                final CheckedTextView s = (CheckedTextView) inflater.inflate(android.R.layout.simple_list_item_single_choice, v, false);
                s.setText(t);
                s.setTag(tt);
                if (tt.equals(current)) {
                    s.setChecked(true);
                    def = true;
                }
                s.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        for (Checkable c : all) {
                            c.setChecked(false);
                        }
                        s.setChecked(true);
                        value = tt;
                        edit.setText(tt);
                        format.setText(pref.getFormatted(tt));
                        edit.setEnabled(false);
                        edit.setClickable(true);
                        check.setChecked(false);
                    }
                });
                ll.addView(s);
                all.add(s);
            }

            edit.setText(current);
            format.setText(pref.getFormatted(current));
            if (def) {
                edit.setEnabled(false);
                edit.setClickable(true);
                check.setChecked(false);
            } else {
                edit.setEnabled(true);
                edit.setClickable(true);
                check.setChecked(true);
            }

            ll.addView(lh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ll.addView(format, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            v.addView(ll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            return v;
        }

        @NonNull
        @Override
        public void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            final NameFormatPreferenceCompat pref = (NameFormatPreferenceCompat) getPreference();
            builder.setTitle(pref.getTitle());
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            final NameFormatPreferenceCompat pref = (NameFormatPreferenceCompat) getPreference();
            if (positiveResult) {
                if (pref.callChangeListener(value)) {
                    pref.setValue(value);
                }
            }
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public NameFormatPreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
        onCreate();
    }

    public String getPredefined(String str) {
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        for (int i = 0; i < text.length; i++) {
            String t = text[i].toString();
            String v = values[i].toString();
            if (v.equals(str))
                return t;
        }
        return null;
    }

    public void onCreate() {
        if (getPositiveButtonText() == null)
            setPositiveButtonText(getContext().getString(android.R.string.ok));
        if (getNegativeButtonText() == null)
            setNegativeButtonText(getContext().getString(android.R.string.cancel));
        CharSequence[] nn = getEntries();
        CharSequence[] vv = getEntryValues();
        if (vv != null) {
            if (nn == null || nn.length != vv.length)
                onAutoFill(vv);
        }
    }

    public void onAutoFill(CharSequence[] vv) {
        ArrayList<CharSequence> list = new ArrayList<>();
        for (CharSequence v : vv)
            list.add(getFormatted(v.toString()));
        setEntries(list.toArray(new CharSequence[0]));
    }

    public String getFormatted(String str) {
        return str;
    }

    @Override
    public CharSequence getSummary() { // crash on some devices while parsing %.. formatting strings
        return getFormatted(getValue());
    }
}
