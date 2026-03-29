package com.github.axet.androidlibrary.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.RemoteViewsCompat;

import java.lang.reflect.Method;

public abstract class AppCompatThemeActivity extends AppCompatActivity {
    public static String TAG = AppCompatThemeActivity.class.getSimpleName();

    public static String SAVE_INSTANCE_STATE = "SAVE_INSTANCE_STATE";
    public static String OVERRIDE_PENDING_TRANSITION = "OVERRIDE_PENDING_TRANSITION";

    public int themeId;
    public int manifestThemeId;

    public ActivityAnimations animations;
    public Handler handler = new Handler();

    public static void showLocked(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED); // enable popup keyboard when locked
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 21)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    public static void showDialogLocked(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        Context context = w.getContext();
        KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (myKM.inKeyguardRestrictedInputMode()) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= 21)
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }

    public static void startHome(Activity a) {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_HOME);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(main);
        a.overridePendingTransition(0, 0);
    }

    public static void moveTaskToBack(Activity a) {
        a.moveTaskToBack(true);
        a.overridePendingTransition(0, 0);
    }

    public static class ActivityAnimations {
        public int activityCloseEnterAnimation;
        public int activityCloseExitAnimation;
        public int activityOpenEnterAnimation;
        public int activityOpenExitAnimation;
        public int windowEnterAnimation;
        public int windowExitAnimation;
        public int windowShowAnimation;
        public int windowHideAnimation;

        public ActivityAnimations(Resources.Theme theme) {
            RemoteViewsCompat.StyledAttrs w = new RemoteViewsCompat.StyledAttrs(theme, new int[]{android.R.attr.windowAnimationStyle});
            int windowAnimationStyleResId = w.getResourceId(android.R.attr.windowAnimationStyle, 0);
            w.close();
            w = new RemoteViewsCompat.StyledAttrs(theme, windowAnimationStyleResId, new int[]{
                    android.R.attr.activityCloseEnterAnimation, android.R.attr.activityCloseExitAnimation,
                    android.R.attr.activityOpenEnterAnimation, android.R.attr.activityOpenExitAnimation,
                    android.R.attr.windowEnterAnimation, android.R.attr.windowExitAnimation,
                    android.R.attr.windowShowAnimation, android.R.attr.windowHideAnimation
            });
            activityCloseEnterAnimation = w.getResourceId(android.R.attr.activityCloseEnterAnimation, 0);
            activityCloseExitAnimation = w.getResourceId(android.R.attr.activityCloseExitAnimation, 0);
            activityOpenEnterAnimation = w.getResourceId(android.R.attr.activityOpenEnterAnimation, 0);
            activityOpenExitAnimation = w.getResourceId(android.R.attr.activityOpenExitAnimation, 0);
            windowEnterAnimation = w.getResourceId(android.R.attr.windowEnterAnimation, 0);
            windowExitAnimation = w.getResourceId(android.R.attr.windowExitAnimation, 0);
            windowShowAnimation = w.getResourceId(android.R.attr.windowShowAnimation, 0);
            windowHideAnimation = w.getResourceId(android.R.attr.windowHideAnimation, 0);
            w.close();
        }
    }

    public static class ScreenReceiver extends BroadcastReceiver {
        public Activity a;
        public Runnable off = new Runnable() {
            @Override
            public void run() { // call once after boot (some phones ignores moveTaskToBack first call after boot)
                try {
                    startHome(a);
                } catch (SecurityException e) { // hueway phones
                    Log.d(TAG, "startHome failed", e);
                    moveTaskToBack(a);
                }
                off = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            moveTaskToBack(a);
                        } catch (Exception e) { // NullPointerException on some 7.0 phones
                            Log.d(TAG, "moveTaskToBack failed", e);
                            startHome(a);
                        }
                    }
                };
            }
        };
        public IntentFilter filter = new IntentFilter();

        public ScreenReceiver() {
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
        }

        public void registerReceiver(Activity a) {
            this.a = a;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.a.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                this.a.registerReceiver(this, filter);
            }
        }

        public void close() {
            a.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a == null)
                return;
            if (a.equals(Intent.ACTION_SCREEN_OFF))
                onScreenOff();
        }

        public void onScreenOff() {
            off.run();
        }
    }

    public AppCompatThemeActivity() {
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
        try {
            if (manifestThemeId == 0)
                manifestThemeId = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA).getThemeResource();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e);
        }
        updateAnimatinos();
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(resid);
    }

    public void updateAnimatinos() { // animations should be manually invoked for @android:style/Theme.Translucent
        Resources.Theme theme = getTheme();
        animations = new ActivityAnimations(theme);
    }

    public int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeLightLib, R.style.AppThemeDarkLib, R.style.AppThemeDarkBlackLib);
    }

    @SuppressLint("RestrictedApi")
    public int getAppThemeBar(Toolbar toolbar) { // old api, need to set theme excplitly for toolbar
        ViewParent parent = toolbar.getParent();
        if (parent instanceof ViewGroup) { // AppBarLayout
            Context t = ((ViewGroup) parent).getContext();
            if (t instanceof ContextThemeWrapper) {
                try {
                    Class<?> clazz = t.getClass();
                    Method method = clazz.getMethod("getThemeResId");
                    return (Integer) method.invoke(t);
                } catch (Exception e) {
                    Log.d(TAG, "unable to get parent theme", e);
                }
            }
            if (t instanceof androidx.appcompat.view.ContextThemeWrapper) {
                return ((androidx.appcompat.view.ContextThemeWrapper) t).getThemeResId();
            }
        }
        return 0;
    }

    public int getAppThemePopup() {
        Log.d(TAG, "Implement getAppThemePopup() when setSupportActionBar is called");
        return getAppTheme();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        ErrorDialog.unhandled(this, true);
    }

    public void checkTranslucent() { // TODO figure it out, how to create transparent initial activity window / themed (black or white) window
        PackageManager pm = getPackageManager();
        try {
            ActivityInfo ai = pm.getActivityInfo(getComponentName(), 0);
            if (ai.theme != android.R.style.Theme_Translucent_NoTitleBar)
                Log.d(TAG, "Please set android:theme=\"@android:style/Theme.Translucent.NoTitleBar\" for themed activity to prevent white blinking on activity start");
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        checkTranslucent();
        setAppTheme(getAppTheme());
        super.onCreate(savedInstanceState == null ? getIntent().getBundleExtra(SAVE_INSTANCE_STATE) : savedInstanceState);
        if (manifestThemeId != themeId && !getIntent().getBooleanExtra(OVERRIDE_PENDING_TRANSITION, false))
            overridePendingTransition(animations.activityOpenEnterAnimation, animations.activityOpenExitAnimation);
        else
            getIntent().removeExtra(OVERRIDE_PENDING_TRANSITION);
    }

    @Override
    public void finish() {
        super.finish();
        if (manifestThemeId != themeId && !getIntent().getBooleanExtra(OVERRIDE_PENDING_TRANSITION, false))
            overridePendingTransition(animations.activityCloseEnterAnimation, animations.activityCloseExitAnimation);
        else
            getIntent().removeExtra(OVERRIDE_PENDING_TRANSITION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeId != getAppTheme())
            restartActivity();
    }

    public void restartActivity() {
        handler.post(new Runnable() {
            @Override
            public void run() { // possible fix FragmentManager ensureExecReady crash
                Bundle out = new Bundle();
                onSaveInstanceState(out);
                restartActivity(new Intent(AppCompatThemeActivity.this, AppCompatThemeActivity.this.getClass())
                        .putExtra(OVERRIDE_PENDING_TRANSITION, true)
                        .putExtra(SAVE_INSTANCE_STATE, out));
            }
        });
    }

    public void restartActivity(Intent intent) {
        getIntent().putExtra(OVERRIDE_PENDING_TRANSITION, true);
        finish();
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        Context theme = getSupportActionBar().getThemedContext();
        int id = getAppThemeBar(toolbar);
        if (theme != null && id != 0) {
            if (theme == this)
                Log.e(TAG, "set 'theme' attribute for Toolbar");
            else
                theme.setTheme(id);
        }
        toolbar.setPopupTheme(getAppThemePopup());
    }
}
