package com.github.axet.androidlibrary.preferences;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.widgets.ThemeUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class TTSPreferenceCompat extends ListPreference {
    public static String ZZ = "[Developer] Accented English";

    public static void showTTS(Context context) {
        if (Build.VERSION.SDK_INT >= 14) {
            Intent intent = new Intent();
            intent.setAction("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            ComponentName componentToLaunch = new ComponentName(
                    "com.android.settings",
                    "com.android.settings.TextToSpeechSettings");
            Intent intent = new Intent();
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(componentToLaunch);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void addLocale(HashSet<Locale> list, Locale l) {
        String s = l.toString();
        if (s == null || s.isEmpty())
            return;
        for (Locale m : list) {
            if (m.toString().equals(s))
                return;
        }
        list.add(l);
    }

    public static Locale toLocale(String str) { // use LocaleUtils.toLocale
        String[] ss = str.split("_");
        if (ss.length == 3)
            return new Locale(ss[0], ss[1], ss[2]);
        else if (ss.length == 2)
            return new Locale(ss[0], ss[1]);
        else
            return new Locale(ss[0]);
    }

    public static HashSet<Locale> getInputLanguages(Context context) {
        HashSet<Locale> list = new HashSet<>();
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            LocaleList ll = LocaleList.getDefault();
            for (int i = 0; i < ll.size(); i++)
                addLocale(list, ll.get(i));
        }
        if (Build.VERSION.SDK_INT >= 11) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                List<InputMethodInfo> ims = imm.getEnabledInputMethodList();
                for (InputMethodInfo m : ims) {
                    List<InputMethodSubtype> ss = imm.getEnabledInputMethodSubtypeList(m, true);
                    for (InputMethodSubtype s : ss) {
                        if (s.getMode().equals("keyboard")) {
                            Locale l = null;
                            if (Build.VERSION.SDK_INT >= 24) {
                                String tag = s.getLanguageTag();
                                if (!tag.isEmpty())
                                    l = Locale.forLanguageTag(tag);
                            }
                            if (l == null)
                                l = toLocale(s.getLocale());
                            addLocale(list, l);
                        }
                    }
                }
            }
        }
        return list;
    }

    public static String formatLocale(Locale l) {
        String n = l.getDisplayLanguage();
        String v = l.toString();
        if (n == null || n.isEmpty() || n.equals(v)) {
            if (v.equals("zz") || v.equals("zz_ZZ"))
                n = ZZ;
            else
                return v;
        }
        return String.format("%s (%s)", n, v);
    }

    public static CharSequence getImageText(final Context context, int res, final int tint) {
        Drawable d = ContextCompat.getDrawable(context, res);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        d = DrawableCompat.wrap(d);
        DrawableCompat.setTint(d, ThemeUtils.getThemeColor(context, tint));
        return getImageText(d);
    }

    public static CharSequence getImageText(Drawable d) {
        SpannableStringBuilder t = new SpannableStringBuilder();
        int start = t.length();
        t.append("!");
        int end = t.length();
        VerticalImageSpan img = new VerticalImageSpan(d);
        t.setSpan(img, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return t;
    }

    public static class VerticalImageSpan extends ImageSpan {
        public VerticalImageSpan(Drawable drawable) {
            super(drawable);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
            Drawable drawable = getDrawable();
            Rect rect = drawable.getBounds();
            if (fontMetricsInt != null) {
                Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
                int fontHeight = fmPaint.descent - fmPaint.ascent;
                int drHeight = rect.bottom - rect.top;
                int centerY = fmPaint.ascent + fontHeight / 2;
                fontMetricsInt.ascent = centerY - drHeight / 2;
                fontMetricsInt.top = fontMetricsInt.ascent;
                fontMetricsInt.bottom = centerY + drHeight / 2;
                fontMetricsInt.descent = fontMetricsInt.bottom;
            }
            return rect.right;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Drawable drawable = getDrawable();
            canvas.save();
            Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
            int fontHeight = fmPaint.descent - fmPaint.ascent;
            int centerY = y + fmPaint.descent - fontHeight / 2;
            int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
            canvas.translate(x, transY);
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    public TTSPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public TTSPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public TTSPreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
        LinkedHashMap<String, String> mm = new LinkedHashMap<>();
        mm.put("", getContext().getString(R.string.system_default));
        HashSet<Locale> ll = getInputLanguages(getContext());
        if (ll.isEmpty())
            ll.add(Locale.US);
        for (Locale l : ll)
            mm.put(l.toString(), formatLocale(l));
        setEntries(mm);
    }

    public void setEntries(LinkedHashMap<String, String> mm) {
        String def = getValue();
        setEntries(mm.values().toArray(new CharSequence[0]));
        setEntryValues(mm.keySet().toArray(new CharSequence[0]));
        int i = findIndexOfValue(def);
        if (i == -1)
            setValueIndex(0);
        else
            setValueIndex(i);
    }

    @Override
    public void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        setSummary(getEntry());
    }

    @Override
    protected void notifyChanged() {
        super.notifyChanged();
        setSummary(getEntry());
    }

    protected void onClick() { // TODO use onPreferenceDisplayDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getTitle());
        String e = getValue();
        int i = findIndexOfValue(e);
        builder.setSingleChoiceItems(getEntries(), i, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setValue(getEntryValues()[which].toString());
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setNeutralButton(getImageText(getContext(), R.drawable.ic_open_in_new_black_24dp, androidx.appcompat.R.attr.colorAccent), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showTTS(getContext());
            }
        });
        AlertDialog d = builder.create();
        d.show();
    }
}
