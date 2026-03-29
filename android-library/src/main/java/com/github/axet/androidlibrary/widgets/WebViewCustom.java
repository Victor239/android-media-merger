package com.github.axet.androidlibrary.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.axet.androidlibrary.R;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.crypto.MD5;
import com.github.axet.androidlibrary.net.HttpClient;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import cz.msebera.android.httpclient.client.methods.HttpGet;

// Custom WebView with POST/GET interception requests.
//
// https://code.google.com/p/android/issues/detail?id=9122#c21
public class WebViewCustom extends WebView {
    public static final String TAG = WebViewCustom.class.getSimpleName();

    public static final String INJECTS_URL = "https://inject/";
    public static final String ABOUT_ERROR = "about:error";

    public static final String METHOD_POST = "POST";
    public static final String METHOD_GET = "GET";

    public static final String SCHEME_HTTP = "http";
    public static final String SCHEME_HTTPS = "https";
    public static final String SCHEME_DATA = "data";
    public static final String SCHEME_JAVASCRIPT = "javascript";

    public static final String HTML_BODY = "body";
    public static final String HTML_HEAD = "head";

    public static final String CONTENTTYPE_JAVASCRIPT = "text/javascript";

    public static final SimpleDateFormat RFC1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

    protected boolean destroyed;

    protected String head;
    protected String js;
    protected String js_post;

    protected String inject;
    protected Thread thread;
    protected Handler handler = new Handler();
    protected HttpClient http;
    protected String base; // since we can't call getUrl() from Chrome IO Thread keep it here
    protected DownloadListener listener;
    protected ArrayList<String> injects = new ArrayList<>();
    protected String html;
    protected ArrayList<WebViewCustom> popups = new ArrayList<>();

    public static Object toJSON(Object object) throws JSONException {
        if (object instanceof Map) {
            JSONObject json = new JSONObject();
            Map map = (Map) object;
            for (Object key : map.keySet()) {
                json.put(key.toString(), toJSON(map.get(key)));
            }
            return json;
        } else if (object instanceof Iterable) {
            JSONArray json = new JSONArray();
            for (Object value : ((Iterable) object)) {
                json.put(value);
            }
            return json;
        } else {
            return object;
        }
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    public static void logIO(String url, Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        if (e instanceof SocketTimeoutException)
            Log.e(TAG, "load timeout " + url);
        else
            Log.e(TAG, url, e);
    }

    @TargetApi(11)
    public static class WebResponse extends android.webkit.WebResourceResponse {
        public WebResponse(String mimeType, String encoding, InputStream data) {
            super(mimeType, encoding, data);
        }

        @TargetApi(21)
        public WebResponse(String mimeType, String encoding, int statusCode, @NonNull String reasonPhrase, Map<String, String> responseHeaders, InputStream data) {
            super(mimeType, encoding, statusCode, reasonPhrase, responseHeaders, data);
        }

        public WebResponse(HttpClient.DownloadResponse w) {
            super(w.mimetype, w.encoding, w.is);
        }
    }

    public class Interceptor {
        @JavascriptInterface
        public void customSubmit(String method, String action, String enctype, String form) { // TODO support enctype
            Log.d(TAG, "customSubmit()");
            if (method.toUpperCase().equals(METHOD_POST)) {
                String url = null;
                try {
                    url = new URL(new URL(base), action).toString();
                    Map<String, String> nvps = new HashMap<>();
                    JSONObject j = new JSONObject(form);
                    JSONArray list = (JSONArray) j.get("form");
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject o = (JSONObject) list.get(i);
                        String key = o.getString("name");
                        String value = o.getString("value");
                        nvps.put(key, value.toString());
                    }
                    postUrl(url, nvps);
                } catch (Exception e) {
                    logIO(url, e);
                    onConsoleMessage(e.getMessage(), 0, "");
                }
                return;
            }
            if (method.toUpperCase().equals(METHOD_GET)) { // even possible? just ignore 'form' then
                String url = null;
                try {
                    url = new URL(new URL(base), action).toString();
                    loadUrl(url);
                } catch (Exception e) {
                    logIO(url, e);
                    onConsoleMessage(e.getMessage(), 0, "");
                }
                return;
            }
        }

        @JavascriptInterface
        public String customAjax(String method, String action, String user, String password, String headers, String form) { // TODO support enctype
            Log.d(TAG, "customAjax()");
            if (method.toUpperCase().equals(METHOD_GET)) {
                String url = null;
                try {
                    url = new URL(new URL(base), action).toString();
                    JSONObject json = new JSONObject(headers);
                    Map<String, Object> hh = toMap(json);
                    HttpClient.DownloadResponse r = get(hh, url);
                    if (r.getError() == null)
                        return r.getHtml();
                } catch (Exception e) {
                    logIO(url, e);
                }
            }
            if (method.toUpperCase().equals(METHOD_POST)) {
                String url = null;
                try {
                    url = new URL(new URL(base), action).toString();
                    HttpClient.DownloadResponse r = post(url, form.getBytes(Charset.defaultCharset()));
                    if (r.getError() == null)
                        return r.getHtml();
                } catch (Exception e) {
                    logIO(url, e);
                }
            }
            return null;
        }
    }

    public WebViewCustom(Context context) {
        super(context);
        create();
    }

    public WebViewCustom(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @TargetApi(11)
    public WebViewCustom(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        super(context, attrs, defStyleAttr, privateBrowsing);
        create();
    }

    public void create() {
        getSettings().setSupportMultipleWindows(true);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= 11)
            getSettings().setDisplayZoomControls(true);

        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                WebViewCustom.this.onProgressChanged(view, newProgress);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return WebViewCustom.this.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
            }

            @Override
            public boolean onConsoleMessage(final ConsoleMessage consoleMessage) {
                return WebViewCustom.this.onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId());
            }

            @Override
            public void onConsoleMessage(String msg, int lineNumber, String sourceID) {
                WebViewCustom.this.onConsoleMessage(msg, lineNumber, sourceID);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
                return WebViewCustom.this.onJsAlert(view, url, message, result);
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                WebViewCustom.this.onPageCommitVisible(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                WebViewCustom.this.onPageFinished(view, url);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                WebViewCustom.this.onReceivedHttpError(view, request, errorResponse);
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                WebViewCustom.this.onReceivedError(view, error.getDescription().toString(), request.getUrl().toString());
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { // on M will becalled above method
                    WebViewCustom.this.onReceivedError(view, description, failingUrl);
                }
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                WebViewCustom.this.onLoadResource(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                WebViewCustom.this.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return WebViewCustom.this.shouldOverrideUrlLoading(view, url);
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getMethod().equals(METHOD_POST)) // post requets come with not data in 'request', ignore at all.
                    return null;
                HttpClient.DownloadResponse w = WebViewCustom.this.shouldInterceptRequest(view, request.getUrl().toString());
                if (w == null)
                    return null;
                return new WebResponse(w);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                HttpClient.DownloadResponse w = WebViewCustom.this.shouldInterceptRequest(view, url);
                if (w == null)
                    return null;
                return new WebResponse(w);
            }
        });
    }

    public void setInterceptionEnable() {
        if (inject != null)
            return;
        try {
            inject = IOUtils.toString(getContext().getResources().openRawResource(R.raw.inject), Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        addJavascriptInterface(new Interceptor(), "interception");
    }

    HttpClient.DownloadResponse getInject(String url) {
        if (url.startsWith(INJECTS_URL)) {
            Uri u = Uri.parse(url);
            int i = Integer.valueOf(u.getPath().substring(1));
            String js = injects.get(i);
            return new HttpClient.DownloadResponse(CONTENTTYPE_JAVASCRIPT, Charset.defaultCharset().name(), js);
        }
        return null;
    }

    public void setHttpClient(HttpClient http) {
        this.http = http;
        setInterceptionEnable();
    }

    public void loadUrlJavaScript(String js) {
        loadUrl(SCHEME_JAVASCRIPT + ":(function(){" + js + "})()");
    }

    @Override
    public void loadUrl(final String url) {
        if (url.startsWith(SCHEME_JAVASCRIPT)) {
            super.loadUrl(url);
            return;
        }
        if (url.startsWith(SCHEME_DATA)) {
            super.loadUrl(url);
            return;
        }

        if (http != null) {
            removeWebCookies(); // make updateCookies() mecanics work
            base = url;
            request(new Runnable() {
                @Override
                public void run() {
                    load(url, getBase(url));
                }
            });
        } else {
            super.loadUrl(url);
        }
    }

    HttpClient.DownloadResponse getResponse(String base, String url) {
        try {
            HttpGet httpGet = new HttpGet(HttpClient.safe(url));
            HttpClient.DownloadResponse r = http.getResponse(base, httpGet);
            r.downloadText();
            return r;
        } catch (RuntimeException e) {
            logIO(url, e);
            return new HttpClient.HttpError(url, e);
        }
    }

    HttpClient.DownloadResponse getResponse(Map<String, Object> headers, String base, String url) {
        try {
            HttpGet httpGet = new HttpGet(HttpClient.safe(url));
            for (String key : headers.keySet())
                httpGet.addHeader(key, (String) headers.get(key));
            HttpClient.DownloadResponse r = http.getResponse(base, httpGet);
            r.downloadText();
            return r;
        } catch (RuntimeException e) {
            logIO(url, e);
            return new HttpClient.HttpError(url, e);
        }
    }

    public HttpClient.DownloadResponse getBase(String url, String html) {
        base = url;
        HttpClient.DownloadResponse r = new HttpClient.DownloadResponse(HttpClient.CONTENTTYPE_HTML, Charset.defaultCharset().name(), html);
        if (r.getError() == null && r.isHtml()) {
            r.setHtml(loadBase(r.getHtml()));
            this.html = r.getHtml();
        }
        return r;
    }

    // if first load url call, get base html page
    public HttpClient.DownloadResponse getBase(String url) {
        if (url.startsWith(SCHEME_DATA)) {
            return null;
        }

        if (http != null) {
            removeWebCookies(); // make updateCookies() mecanics work
        }

        if (base == null || url.equals(base)) {
            base = url;
            HttpClient.DownloadResponse r = getResponse(base, url);
            if (r.getError() == null && r.isHtml()) {
                r.setHtml(loadBase(r.getHtml()));
                this.html = r.getHtml();
            }
            return r;
        } else {
            return get(url);
        }
    }

    @Override
    public void stopLoading() {
        super.stopLoading();
        if (http != null && http.getRequest() != null) {
            if (thread != null) {
                thread.interrupt();
            }
            thread = new Thread(new Runnable() { // netowkr on main thread
                @Override
                public void run() {
                    http.abort();
                }
            }, "WebViewCustom abort thread");
            thread.start();
        }
    }

    void request(Runnable run) { // network on main thread preventer
        thread = new Thread(run, "WebViewCustom request");
        thread.start();
    }

    @Override
    public void goBack() {
        super.goBack();
        base = getOriginalUrl();
    }

    @Override
    public String getOriginalUrl() {
        String url = super.getOriginalUrl();
        if (url == null)
            return null;
        if (url.startsWith(SCHEME_DATA)) // bug, it suppose to be normal url
            return getUrl();
        return url;
    }

    @Override
    public void goForward() {
        super.goForward();
        base = getOriginalUrl();
    }

    @Override
    public void reload() {
        super.reload();
        base = getOriginalUrl();
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        if (http != null) {
            removeWebCookies(); // make updateCookies() mecanics work
            base = url;
            load(url, post(url, postData));
        } else {
            super.postUrl(url, postData);
        }
    }

    public void postUrl(String url, Map<String, String> postData) {
        if (http != null) {
            String hist = url;

            HttpClient.DownloadResponse r = post(url, postData);
            if (r.getError() != null) {
                // we need update url to make WebView reset previous page zoom. all calls come from/to WebView and it knows it is new url.
                // so getBase() works fine. only postUrl() sholud do the trick.
                url = ABOUT_ERROR;
                // keep history url points to original url, so WebView.reload() keep working properly
            } else if (r.getBuf() == null || r.getBuf().length == 0) { // inside page post's do not reload page
                return;
            }

            base = url;
            load(url, hist, r);
        } else {
            super.postUrl(url, HttpClient.encode(postData).getBytes(Charset.defaultCharset()));
        }
    }

    public void load(String url, final HttpClient.DownloadResponse r) { // Network on main Thread
        load(url, url, r);
    }

    public void load(String url, String hist, final HttpClient.DownloadResponse r) {
        if (!r.downloaded) {
            listener.onDownloadStart(url, r.userAgent, r.contentDisposition, r.mimetype, r.contentLength);
            return;
        }
        try {
            url = r.getUrl(); // error or not, we need to keep javascript working

            String html = IOUtils.toString(r.is, r.encoding);
            if (r.getError() == null) {
                hist = url; // no error, we have to get last redirected url
                if (r.isHtml())
                    html = loadBase(html);
            }

            base = url;

            final String baseUrl = url;
            final String data = html;
            final String history = hist;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    loadHtmlWithBaseURL(baseUrl, data, history);
                }
            });
        } catch (final IOException e) {
            logIO(url, e);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onConsoleMessage(e.getMessage(), 0, "");
                }
            });
        }
    }

    public void loadHtmlWithBaseURL(String baseUrl, HttpClient.DownloadResponse html, String historyUrl) {
        if (html.getError() != null) // on errors do not call loadBase
            base = baseUrl;
        loadHtmlWithBaseURL(baseUrl, html.getHtml(), historyUrl);
    }

    public void loadHtmlWithBaseURL(String baseUrl, String html, String historyUrl) {
        loadDataWithBaseURL(baseUrl, html, HttpClient.CONTENTTYPE_HTML, Charset.defaultCharset().name(), historyUrl);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        // all inner callers already set url
        if (base == null || !base.equals(baseUrl)) { // external call
            if (http != null) { // make updateCookies() mechanics work
                removeWebCookies();
            }
            base = baseUrl;
            data = loadBase(data);
        }
        this.html = data;

        if (destroyed)
            return; // old api crash

        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    protected String loadBase(String data) {
        Document doc = Jsoup.parse(data);
        return loadBase(doc);
    }

    protected String loadBase(Document doc) {
        Element head = doc.getElementsByTag(HTML_HEAD).first();
        if (head != null) {
            if (this.head != null)
                head.prepend(this.head);
            if (inject != null)
                head.prepend(addInject(inject));
        }
        if (js != null) {
            Element body = doc.getElementsByTag(HTML_BODY).first();
            if (body != null)
                body.append(addInject(js));
        }
        return doc.outerHtml();
    }

    protected String expandInject(String js) {
        return "<script type='" + CONTENTTYPE_JAVASCRIPT + "'>\n" + js + "\n</script>";
    }

    protected String addInject(String js) {
        int i = injects.size();
        injects.add(js);
        return "<script type='" + CONTENTTYPE_JAVASCRIPT + "' src='" + INJECTS_URL + i + "?md5=" + MD5.digest(js) + "'/>";
    }

    @Override
    public void setDownloadListener(DownloadListener listener) {
        super.setDownloadListener(listener);
        this.listener = listener;
    }

    // javascript can add cookies. update every new request;
    protected void updateCookies(String url) {
        CookieManager inst = CookieManager.getInstance();

        if (Build.VERSION.SDK_INT < 21) {
            CookieSyncManager.getInstance().sync();
        } else {
            inst.flush();
        }

        String cookies = inst.getCookie(url);
        if (cookies != null && !cookies.isEmpty())
            http.addCookies(url, cookies);
    }

    public HttpClient.DownloadResponse get(Map<String, Object> headers, final String url) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                onLoadResource(WebViewCustom.this, url);
            }
        });

        updateCookies(url);

        try {
            HttpClient.DownloadResponse r = getResponse(headers, base, url);
            return r;
        } catch (final RuntimeException e) {
            logIO(url, e);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Throwable t = e;
                    while (t.getCause() != null)
                        t = t.getCause();
                    onReceivedError(WebViewCustom.this, t.getMessage(), url);
                }
            });
            return new HttpClient.HttpError(url, e);
        }
    }

    public HttpClient.DownloadResponse get(final String url) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                onLoadResource(WebViewCustom.this, url);
            }
        });

        updateCookies(url);

        try {
            HttpClient.DownloadResponse r = getResponse(base, url);
            return r;
        } catch (final RuntimeException e) {
            logIO(url, e);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Throwable t = e;
                    while (t.getCause() != null)
                        t = t.getCause();
                    onReceivedError(WebViewCustom.this, t.getMessage(), url);
                }
            });
            return new HttpClient.HttpError(url, e);
        }
    }

    public HttpClient.DownloadResponse post(String url, byte[] postData) {
        Map<String, String> map = new HashMap<>();
        String data = new String(postData, Charset.defaultCharset());
        UrlQuerySanitizer sanitizer = new UrlQuerySanitizer("?" + data);
        List<UrlQuerySanitizer.ParameterValuePair> list = sanitizer.getParameterList();
        for (int i = 0; i < list.size(); i++) {
            UrlQuerySanitizer.ParameterValuePair p = list.get(i);
            map.put(p.mParameter, p.mValue);
        }
        return post(url, map);
    }

    public HttpClient.DownloadResponse post(String url, Map<String, String> postData) {
        updateCookies(url);
        try {
            HttpClient.DownloadResponse r = http.postResponse(base, url, postData);
            r.downloadText();
            return r;
        } catch (RuntimeException e) {
            logIO(url, e);
            return new HttpClient.HttpError(url, e);
        }
    }

    public void onProgressChanged(WebView view, int newProgress) {
    }

    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        Log.d(TAG, "onCreateWindow()");
        WebViewCustom newWebView = new WebViewCustom(getContext()) {
            @Override
            public void loadUrl(String url) {
                WebViewCustom.this.loadUrl(url);
            }
        };
        if (http != null)
            newWebView.setHttpClient(http);
        if (listener != null)
            newWebView.setDownloadListener(listener);
        popups.add(newWebView);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();
        return true;
    }

    public boolean onConsoleMessage(String msg, int lineNumber, String sourceID) {
        String line = formatInjectError(sourceID, lineNumber);
        Log.d(TAG, "onConsoleMessage: " + msg + ", " + "[" + lineNumber + "] " + line + ", " + sourceID);
        return true;
    }

    public String formatInjectError(String url, int line) {
        String js = null;
        if (url == null || url.isEmpty()) { // null? then it is js_post call
            js = js_post;
        } else {
            HttpClient.DownloadResponse r = getInject(url);
            if (r != null) {
                js = r.getHtml();
            }
        }
        if (js == null) // no known script error
            return "";
        String[] lines = js.split("\n");
        int t = line - 1; // get script line
        if (t < 0)
            return "";
        if (t < lines.length)
            return lines[t];
        return ""; // show no line
    }

    public boolean onJsAlert(WebView view, String url, final String message, JsResult result) {
        Log.d(TAG, message);
        result.confirm();
        return true;
    }

    public void onPageFinished(WebView view, String url) {
        Log.d(TAG, "onPageFinished");
        if (js_post != null)
            loadUrlJavaScript(js_post);
    }

    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
    }

    public void onPageCommitVisible(WebView view, String url) {
        Log.d(TAG, "onPageCommitVisible");
    }

    public void onReceivedError(WebView view, String message, String url) {
        Log.d(TAG, message);
    }

    public void onLoadResource(WebView view, String url) {
    }

    public void onPageStarted(WebView view, String url, Bitmap favicon) {
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        loadUrl(url); // user did not overide it. load page using our code, we may need to inject.
        return true;
    }

    public HttpClient.DownloadResponse shouldInterceptRequest(WebView view, String url) {
        HttpClient.DownloadResponse r = getInject(url);
        if (r != null)
            return r;
        if (http != null)
            return getBase(url);
        return null;
    }

    public void setHead(String head) {
        this.head = head;
    }

    public void setInject(String js) {
        this.js = js;
    }

    public void setInjectPost(String js) {
        this.js_post = js;
    }

    public String getHtml() {
        return html;
    }

    // not working. use removeAllCookies() then add ones you need.
    public void clearCookies(String url) {
        CookieManager inst = CookieManager.getInstance();
        String cookies = inst.getCookie(url); // longer url better, domain only can return null

        Uri uri = Uri.parse(url);
        String domain = uri.getAuthority();

        if (cookies != null) {
            // we need to set expires, otherwise WebView will keep deleted cookies forever ("name=")
            String expires = "expires=Thu, 01 Jan 1970 03:00:00 GMT"; // # date -r 0 +%a,\ %d\ %b\ %Y\ %H:%M:%S\ GMT

            RFC1123.setTimeZone(TimeZone.getTimeZone("GMT"));
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 1);
            expires = "expires=" + RFC1123.format(cal.getTime());

            if (Build.VERSION.SDK_INT < 21) {
                CookieSyncManager.createInstance(getContext());
                CookieSyncManager.getInstance().startSync();
            }
            String[] cc = cookies.split(";");
            for (String c : cc) {
                String[] vv = c.split("=", 2);
                for (File f = Storage.getFile(uri); f != null; f = f.getParentFile()) {
                    String p;
                    String path;
                    if (f.equals(new File(File.separator))) {
                        p = "";
                        path = "";
                    } else {
                        p = f.getPath();
                        path = "; path=" + p;
                    }
                    String cookie = vv[0].trim() + "=" + "; domain=" + uri.getAuthority() + path + "; " + expires;
                    String u = new Uri.Builder().scheme(SCHEME_HTTP).authority(domain).path(p).build().toString();
                    inst.setCookie(u, cookie);
                }
            }
            if (Build.VERSION.SDK_INT < 21) {
                CookieSyncManager.getInstance().stopSync();
                CookieSyncManager.getInstance().sync();
                inst.removeSessionCookie();
                inst.removeExpiredCookie();
            } else {
                inst.flush();
                inst.removeSessionCookies(null);
            }
        }
    }

    public static boolean setCookies2Apache(String url, HttpClient apacheStore) {
        String cookies = CookieManager.getInstance().getCookie(url); // longer url better, domain only can return null
        if (cookies == null || cookies.isEmpty())
            return false;

        String[] cc = cookies.split(";");

        // check if we have cookies with same name. if yes. then webview set cookie for a different URL.
        // then we have to remove old cookie from apache store (check value apache==webview) and
        // replace it with new cookie from webview. This is come from WebView restriction, when we do not know
        // exact cookie domain/path. and only can call getCookie(url)

        ArrayList<HttpCookie> webviewStore = new ArrayList<>();

        Uri uri = Uri.parse(url);

        for (String c : cc) {
            String[] vv = c.split("=", 2);
            String n = null;
            if (vv.length > 0)
                n = vv[0].trim();
            String v = null;
            if (vv.length > 1)
                v = vv[1].trim();
            if (n == null)
                continue;
            HttpCookie cookie = new HttpCookie(n, v);
            // it may cause troubles. Cookie maybe set for domain, .domain, www.domain or www.domain/path
            // and since we have to cut all www/path same name cookies with different paths will override.
            // need to check if returned cookie sting can contains DOMAIN/PATH values. Until then use domain only.
            cookie.setDomain(uri.getAuthority());
            webviewStore.add(cookie);
        }

        Set<String> dups = new TreeSet<>();

        for (int i = 0; i < webviewStore.size(); i++) {
            for (int k = 0; k < webviewStore.size(); k++) {
                if (k == i)
                    continue;
                String n1 = webviewStore.get(i).getName();
                if (n1.equals(webviewStore.get(k).getName()))
                    dups.add(n1);
            }
        }

        for (String d : dups) { // find dups in Apache store. delete same cookie by values from WebView store
            for (int i = apacheStore.getCount() - 1; i >= 0; i--) {
                HttpCookie c = apacheStore.getCookie(i);
                String n = c.getName();
                if (n.equals(d)) {
                    String v = c.getValue();
                    for (int k = webviewStore.size() - 1; k >= 0; k--) { // remove WebView cookies, which name&&value == apache store
                        HttpCookie cw = webviewStore.get(k);
                        if (cw.getName().equals(n) && cw.getValue().equals(v)) {
                            webviewStore.remove(k);
                        }
                    }
                    HttpCookie rm = new HttpCookie(n, v);  // remove from apache store
                    rm.setPath(c.getPath());
                    rm.setDomain(c.getDomain());
                    apacheStore.removeCookie(rm);
                }
            }
        }

        for (HttpCookie c : webviewStore) // add remaining cookies from WebView store to Apache store
            apacheStore.addCookie(c);

        // since we have duplicates, (same cookies with different path. one set by setCookies2WebView
        // another set by WebView server call. drop them all.
        if (dups.size() != 0) {
            if (Build.VERSION.SDK_INT >= 21)
                CookieManager.getInstance().removeAllCookies(null);
            else
                CookieManager.getInstance().removeAllCookie();
        }

        return true;
    }

    public static void setCookies2WebView(Context context, HttpClient cookieStore) {
        if (cookieStore != null) { // share cookies back (Apache --> WebView)
            CookieSyncManager.createInstance(context);
            CookieManager m = CookieManager.getInstance();
            for (int i = 0; i < cookieStore.getCount(); i++) {
                HttpCookie c = cookieStore.getCookie(i);
                Uri.Builder b = new Uri.Builder();
                if (c.getSecure())
                    b.scheme(SCHEME_HTTPS);
                else
                    b.scheme(SCHEME_HTTP);
                b.authority(c.getDomain());
                if (c.getPath() != null) {
                    b.appendPath(c.getPath());
                }
                String url = b.build().toString();
                m.setCookie(url, c.getName() + "=" + c.getValue());
            }
            CookieSyncManager.getInstance().sync();
        }
    }

    public void removeAllCookies() {
        if (http != null) {
            http.clearCookies();
            return;
        }
        removeWebCookies();
    }

    public void removeWebCookies() {
        if (Build.VERSION.SDK_INT >= 21)
            CookieManager.getInstance().removeAllCookies(null);
        else
            CookieManager.getInstance().removeAllCookie();
    }

    @Override
    public void destroy() {
        super.destroy();
        destroyed = true;
        for (WebViewCustom w : popups)
            w.destroy();
        popups.clear();
    }
}
