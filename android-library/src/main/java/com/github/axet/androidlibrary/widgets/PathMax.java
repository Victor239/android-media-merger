package com.github.axet.androidlibrary.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

// TextEdit android:ellipsize="middle" with andorid:singleline="true"
public class PathMax extends ViewGroup {
    public static final String ROOT = "/";
    public static final String MID = "…"; // &#8230;

    String s = ROOT;

    // speed up calculations for specified width
    HashMap<Integer, String> textMap = new HashMap<>();

    boolean ignore = false;

    // xml call
    public PathMax(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    // xml call
    public PathMax(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // created manualu
    public PathMax(Context context, TextView text) {
        super(context);
        addView(text);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        super.addView(child, index, params);
        attach(child);
    }

    void attach(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    save();
                }
            });
            save();
        }
    }

    void save() {
        if (ignore)
            return;

        TextView text = (TextView) getChildAt(0);
        String t = text.getText().toString();
        if (!s.equals(t)) {
            s = t;
            textMap.clear();
        }
    }

    public String makePath(String prefix, List<String> ss, String separator, String suffix) {
        if (ss.size() == 0)
            return ROOT;
        return prefix + TextUtils.join(separator, ss) + suffix;
    }

    public List<String> splitPath(String s) {
        return new ArrayList<>(Arrays.asList(s.split(Pattern.quote(File.separator))));
    }

    int getMaxWidth() {
        TextView text = (TextView) getChildAt(0);
        return text.getWidth() - text.getPaddingLeft() - text.getPaddingRight() - getPaddingLeft() - getPaddingRight();
    }

    void set(String s) {
        TextView text = (TextView) getChildAt(0);
        String old = text.getText().toString();
        if (!old.equals(s)) {
            ignore = true;
            text.setText(s);
            ignore = false;
        }
    }

    int measureText(String s) {
        TextView text = (TextView) getChildAt(0);
        set(s);
        text.measure(0, 0);
        return text.getMeasuredWidth();
    }

    public int formatText(int max) {
        String s = this.s;

        String scheme = "";
        try {
            URI u = new URI(s);
            if (u.getScheme() != null) {
                scheme = u.getScheme() + "://";
                s = s.substring(scheme.length());
            }
        } catch (URISyntaxException e) {
        }

        List<String> ss = splitPath(s);

        String suffix = s.endsWith(File.separator) ? File.separator : "";

        String d;

        // when s == "/"
        if (ss.size() == 0) {
            d = scheme + s;
        } else {
            d = dots(max, ss, scheme, File.separator, suffix);
        }

        return measureText(d);
    }

    String dots(int max, List<String> ss, String scheme, String separator, String suffix) {
        boolean removed = false;

        List<String> ssdots = ss;

        String sdots = makePath(scheme, ssdots, separator, suffix);

        while (measureText(sdots) > max) {
            if (ss.size() == 3) {
                int mid = 1;
                ssdots = new ArrayList<>(ss);
                ssdots.set(mid, MID);
                removed = true;
                ss.remove(mid);
                sdots = makePath(scheme, ssdots, separator, suffix);
            } else if (ss.size() == 2) {
                String sdot = ss.get(1);
                if (sdot.length() <= 2) {
                    int mid = 1; // for 2 elements, remove second one
                    ssdots = new ArrayList<>(ss);
                    ssdots.set(mid, MID);
                    ss.remove(mid);
                    removed = true;
                    sdots = makePath(scheme, ssdots, separator, suffix);
                } else {
                    int mid = sdot.length() / 2;

                    sdot = sdot.substring(0, mid) + sdot.substring(mid + 1, sdot.length()); // cut mid char

                    ss.set(1, sdot);

                    sdot = sdot.substring(0, mid) + MID + sdot.substring(mid, sdot.length());

                    if (removed) {
                        if (scheme.isEmpty())
                            sdot = MID + separator + sdot;
                    }

                    sdots = scheme + ss.get(0) + separator + sdot + suffix;
                }
            } else if (ss.size() == 1) {
                String sdot = ss.get(0);

                if (sdot.length() <= 2) { // cant go lower return
                    return scheme + MID;
                }

                int mid = sdot.length() / 2;
                sdot = sdot.substring(0, mid) + sdot.substring(mid + 1, sdot.length()); // cut mid char

                ss.set(0, sdot);

                sdot = sdot.substring(0, mid) + MID + sdot.substring(mid, sdot.length());

                if (removed) {
                    sdot = sdot + separator + MID;
                }

                sdots = scheme + sdot + suffix;
            } else {
                int mid = (ss.size() - 1) / 2 + 1;
                ssdots = new ArrayList<>(ss);
                ssdots.set(mid, MID);
                removed = true;
                ss.remove(mid);
                sdots = makePath(scheme, ssdots, separator, suffix);
            }
        }

        return sdots;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        TextView text = (TextView) getChildAt(0);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        int padx = getPaddingLeft() + getPaddingRight();
        int pady = getPaddingTop() + getPaddingBottom();

        int textWidth;
        int pathWidth;

        int textHeight;
        int pathHeight;

        int w = widthSize - padx;
        String s = textMap.get(w);
        if (s == null) {
            textWidth = formatText(w);
            textMap.put(w, text.getText().toString());
        } else {
            textWidth = measureText(s);
        }

        if (widthMode == MeasureSpec.EXACTLY) {
            pathWidth = widthSize;
        } else {
            pathWidth = textWidth + padx;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            textHeight = heightSize - pady;
            pathHeight = heightSize;
        } else {
            text.measure(0, 0);
            textHeight = text.getMeasuredHeight();
            pathHeight = textHeight + pady;
        }

        text.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textHeight, MeasureSpec.EXACTLY));

        setMeasuredDimension(pathWidth, pathHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        TextView text = (TextView) getChildAt(0);
        text.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + text.getMeasuredWidth(), getPaddingTop() + text.getMeasuredHeight());
    }
}
