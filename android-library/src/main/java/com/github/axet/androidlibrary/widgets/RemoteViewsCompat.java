package com.github.axet.androidlibrary.widgets;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import androidx.core.content.ContextCompat;
import androidx.appcompat.view.ContextThemeWrapper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.R;

import java.util.Arrays;

public class RemoteViewsCompat extends RemoteViews {
    public static final String TAG = RemoteViewsCompat.class.getSimpleName();

    public Context context;
    public View view;

    public static class StyledAttrs {
        public int[] aa;
        public TypedArray ta;

        public StyledAttrs(int[] aa) {
            this.aa = aa;
            Arrays.sort(this.aa); // know bug https://stackoverflow.com/questions/19034597
        }

        public StyledAttrs(Resources.Theme theme, int[] aa) {
            this(aa);
            ta = theme.obtainStyledAttributes(this.aa);
        }

        public StyledAttrs(Resources.Theme theme, int id, int[] aa) {
            this(aa);
            ta = theme.obtainStyledAttributes(id, this.aa);
        }

        public StyledAttrs(Resources.Theme theme, AttributeSet attrs, int[] aa) {
            this(aa);
            ta = theme.obtainStyledAttributes(attrs, this.aa, 0, 0);
        }

        public int getResourceId(int id, int def) {
            return ta.getResourceId(find(id), def);
        }

        public boolean getValue(int id, TypedValue out) {
            return ta.getValue(find(id), out);
        }

        public boolean hasValue(int id) {
            return ta.hasValue(find(id));
        }

        public int find(int id) {
            return Arrays.binarySearch(aa, id);
        }

        public void close() {
            ta.recycle();
        }
    }

    public static class ThemeFactory implements LayoutInflater.Factory {
        public Context context;
        public RemoteViews view;

        public ThemeFactory(Context context, RemoteViews view) {
            this.context = context;
            this.view = view;
        }

        @Override
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (Build.VERSION.SDK_INT >= 21 && this.context != context) // API21+ and 'android:theme' applied = ignore
                return null;

            Resources.Theme theme = context.getTheme();

            StyledAttrs w = new StyledAttrs(theme, attrs, new int[]{
                    android.R.attr.id,
                    android.R.attr.background,
                    android.R.attr.tint,
                    android.R.attr.textColor,
            });

            TypedValue out = new TypedValue();
            if (w.getValue(android.R.attr.id, out)) {
                int id = out.resourceId;
                if (w.getValue(android.R.attr.background, out))
                    setBackgroundColor(view, id, getColor(context, out));
                if (w.getValue(android.R.attr.textColor, out))
                    view.setTextColor(id, getColor(context, out));
                if (w.getValue(android.R.attr.tint, out))
                    setImageViewTint(view, id, getColor(context, out));
                if (name.equals(Button.class.getSimpleName())) {
                    if (Build.VERSION.SDK_INT <= 10) { // seems like API10 and below does not support notification buttons
                        view.setViewVisibility(id, View.GONE);
                    } else {
                        if (!w.hasValue(android.R.attr.background)) { // no background set
                            int res = getButtonBackground(theme, context);
                            if (res != 0)
                                setBackgroundResource(view, id, res);
                        }
                    }
                }
                if (name.equals(ImageButton.class.getSimpleName())) {
                    if (Build.VERSION.SDK_INT <= 10) { // seems like API10 and below does not support notification buttons
                        view.setViewVisibility(id, View.GONE);
                    } else {
                        if (!w.hasValue(android.R.attr.background)) { // no background set
                            int res = getImageButtonBackground(theme, context);
                            if (res != 0)
                                setBackgroundResource(view, id, res);
                        }
                    }
                }
            }
            w.close();
            return null;
        }

        public int getColor(Context context, TypedValue out) {
            if (out.type == TypedValue.TYPE_STRING)
                out.data = ContextCompat.getColor(context, out.resourceId); // xml color selector
            return out.data;
        }

        @SuppressLint("RestrictedApi")
        public int getButtonStyle(Resources.Theme theme, Context context) {
            TypedValue style = new TypedValue();
            if (theme.resolveAttribute(androidx.appcompat.R.attr.buttonStyle, style, true)) {
                if (style.resourceId == androidx.appcompat.R.style.Widget_AppCompat_Button) {
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style.resourceId);
                    Resources.Theme t = w.getTheme();
                    TypedValue out = new TypedValue();
                    if (t.resolveAttribute(android.R.attr.background, out, true)) {
                        if (out.string != null) {
                            String[] ss = new String[]{
                                    "res/drawable/btn_default_material.xml", // API21
                                    "res/drawable/abc_btn_default_mtrl_shape.xml" // API16
                            };
                            for (String s : ss) {
                                if (out.string.equals(s)) { // AppCompat material button
                                    if (t.resolveAttribute(android.R.attr.buttonStyle, out, true)) { // which theme light or dark?
                                        switch (out.resourceId) {
                                            case android.R.style.Widget_Holo_Button:
                                            case android.R.style.Widget_Material_Button:
                                                return android.R.style.Widget_Material_Button;
                                            case android.R.style.Widget_Holo_Light_Button:
                                            case android.R.style.Widget_Material_Light_Button:
                                                return android.R.style.Widget_Material_Light_Button;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return style.resourceId;
            }
            return 0;
        }

        @SuppressLint("RestrictedApi")
        public int getImageButtonStyle(Resources.Theme theme, Context context) {
            TypedValue style = new TypedValue();
            if (theme.resolveAttribute(androidx.appcompat.R.attr.imageButtonStyle, style, true)) {
                if (style.resourceId == androidx.appcompat.R.style.Widget_AppCompat_ImageButton) {
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style.resourceId);
                    Resources.Theme t = w.getTheme();
                    TypedValue out = new TypedValue();
                    if (t.resolveAttribute(android.R.attr.background, out, true)) {
                        if (out.string != null) {
                            String[] ss = new String[]{
                                    "res/drawable/btn_default_material.xml", // API21
                                    "res/drawable/abc_btn_default_mtrl_shape.xml" // API16
                            };
                            for (String s : ss) {
                                if (out.string.equals(s)) { // AppCompat material button
                                    if (t.resolveAttribute(android.R.attr.imageButtonStyle, out, true)) { // which theme light or dark?
                                        switch (out.resourceId) {
                                            case android.R.style.Widget_Holo_ImageButton:
                                            case android.R.style.Widget_Material_ImageButton:
                                                return android.R.style.Widget_Material_ImageButton;
                                            case android.R.style.Widget_Holo_Light_ImageButton:
                                            case android.R.style.Widget_Material_Light_ImageButton:
                                                return android.R.style.Widget_Material_Light_ImageButton;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return style.resourceId;
            }
            return 0;
        }

        @SuppressLint("RestrictedApi")
        public int getButtonBackground(Resources.Theme theme, Context context) {
            TypedValue out = new TypedValue();
            int style = getButtonStyle(theme, context);
            switch (style) {
                case android.R.style.Widget_Material_Button:
                    return R.drawable.remoteview_btn_dark;
                case android.R.style.Widget_Material_Light_Button:
                    return R.drawable.remoteview_btn_light;
                case 0:
                    break;
                default:
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style);
                    Resources.Theme t = w.getTheme();
                    if (t.resolveAttribute(android.R.attr.background, out, true))
                        return out.resourceId;
            }
            return 0;
        }

        @SuppressLint("RestrictedApi")
        public int getImageButtonBackground(Resources.Theme theme, Context context) {
            TypedValue out = new TypedValue();
            int style = getImageButtonStyle(theme, context);
            switch (style) {
                case android.R.style.Widget_Material_ImageButton:
                    return R.drawable.remoteview_btn_dark;
                case android.R.style.Widget_Material_Light_ImageButton:
                    return R.drawable.remoteview_btn_light;
                case 0:
                    break;
                default:
                    ContextThemeWrapper w = new ContextThemeWrapper(context, style);
                    Resources.Theme t = w.getTheme();
                    if (t.resolveAttribute(android.R.attr.background, out, true))
                        return out.resourceId;
            }
            return 0;
        }
    }

    public static void setBackgroundColor(RemoteViews view, int id, int color) {
        view.setInt(id, "setBackgroundColor", color);
    }

    public static void setBackgroundResource(RemoteViews view, int id, int res) {
        view.setInt(id, "setBackgroundResource", res);
    }

    public static void setImageViewTint(RemoteViews view, int id, int color) {
        view.setInt(id, "setColorFilter", color);
    }

    public static void setContentDescription(RemoteViews view, int id, CharSequence text) {
        if (Build.VERSION.SDK_INT >= 15) // RemotableViewMethod.class annotation starting from 4.0.3
            view.setCharSequence(id, "setContentDescription", text);
    }

    public static int findAttr(AttributeSet attrs, String name) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeName(i).equals(name))
                return i;
        }
        return -1;
    }

    public static int findAttr(AttributeSet attrs, int id) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) == id)
                return i;
        }
        return -1;
    }

    public static int getAttributeAttributeValue(AttributeSet attrs, int index) {
        String v = attrs.getAttributeValue(index);
        if (v.startsWith("?"))
            return Integer.valueOf(v.substring(1));
        return 0; // invalid resource
    }

    public static boolean getAttr(AttributeSet attrs, int id, TypedValue out) {
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            if (attrs.getAttributeNameResource(i) == id) {
                String v = attrs.getAttributeValue(i);
                switch (v.charAt(0)) {
                    case '?':
                        out.type = TypedValue.TYPE_ATTRIBUTE;
                        out.resourceId = Integer.valueOf(v.substring(1));
                        break;
                    case '@':
                        out.type = TypedValue.TYPE_REFERENCE;
                        out.resourceId = Integer.valueOf(v.substring(1));
                        break;
                }
                return true;
            }
        }
        return false;
    }

    public static void applyTheme(final Context context, final RemoteViews view) {
        applyTheme(context, view, new ThemeFactory(context, view));
    }

    public static void applyTheme(final Context context, final RemoteViews view, LayoutInflater.Factory factory) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater = inflater.cloneInContext(context);
        inflater.setFactory(factory);
        inflater.inflate(view.getLayoutId(), null);
    }

    public static void mergeRemoteViews(RemoteViews view, RemoteViews a) {
        try {
            view.getClass().getDeclaredMethod("mergeRemoteViews", RemoteViews.class).invoke(view, a);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RemoteViewsCompat(Context context, String packageName, int layoutId) {
        super(packageName, layoutId);
        this.context = context;
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(layoutId, null);
    }

    @Override
    public void setCharSequence(int viewId, String methodName, CharSequence value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setCharSequence(viewId, methodName, value);
    }

    @Override
    public void setString(int viewId, String methodName, String value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setString(viewId, methodName, value);
    }

    @Override
    public void setInt(int viewId, String methodName, int value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setInt(viewId, methodName, value);
    }

    @Override
    public void setBitmap(int viewId, String methodName, Bitmap value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setBitmap(viewId, methodName, value);
    }

    @Override
    public void setIntent(int viewId, String methodName, Intent value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setIntent(viewId, methodName, value);
    }

    @Override
    public void setIcon(int viewId, String methodName, Icon value) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setIcon(viewId, methodName, value);
    }

    @Override
    public void setViewPadding(int viewId, int left, int top, int right, int bottom) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setViewPadding(viewId, left, top, right, bottom);
    }

    @Override
    public void setOnClickPendingIntent(int viewId, PendingIntent pendingIntent) {
        if (view.findViewById(viewId) == null) // API9 crash if view does not exists
            return;
        super.setOnClickPendingIntent(viewId, pendingIntent);
    }
}
