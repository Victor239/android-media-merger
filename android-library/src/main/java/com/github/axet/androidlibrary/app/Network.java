package com.github.axet.androidlibrary.app;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.util.Log;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Network {
    public static final String TAG = Network.class.getSimpleName();

    public static String LOCAL = "127.0.0.1";
    public static String DNS = "google.com";

    public static String format(int ip) {
        return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    public static String getprop(String prop) {
        try {
            Class SystemProperties = Class.forName("android.os.SystemProperties");
            Method method = SystemProperties.getMethod("get", new Class[]{String.class});
            return (String) method.invoke(null, new Object[]{prop});
        } catch (Exception e) {
            return null;
        }
    }

    public static String getWifiInterface() {
        return getprop("wifi.interface");
    }

    @SuppressLint("MissingPermission") // ACCESS_WIFI_STATE
    public static String getGatewayIP(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null) {
                for (RouteInfo routeInfo : lp.getRoutes()) {
                    if (routeInfo.isDefaultRoute() && routeInfo.getGateway() != null) {
                        try {
                            return InetAddress.getByAddress(routeInfo.getGateway().getAddress()).getHostAddress();
                        } catch (UnknownHostException e) {
                            Log.w(TAG, e);
                        }
                    }
                }
            }
        } else {
            try {
                String v = getWifiInterface();
                if (v != null && !v.isEmpty()) {
                    v = getprop("dhcp." + v + ".gateway");
                    if (v != null && !v.isEmpty())
                        return v;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, e);
            }
        }
        final WifiManager w = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE); // must be on application context
        if (w != null) {
            DhcpInfo d = w.getDhcpInfo();
            if (d != null)
                return format(d.gateway);
        }
        return null;
    }

    public static boolean ping(String ip, boolean ipv6) {
        PingExt ping = new PingExt(ip, ipv6);
        return ping.ping();
    }

    public static boolean pingLocal() {
        return ping(LOCAL, false);
    }

    public static boolean ping(int ip) {
        return ping(format(ip), false);
    }

    public static boolean ping(String ip) {
        return ping(ip, ip.contains(":"));
    }

    public static boolean dns() {
        InetAddress a = null;
        try {
            a = InetAddress.getByName(DNS);
        } catch (UnknownHostException ignore) {
        }
        return a != null;
    }
}
