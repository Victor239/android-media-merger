package com.github.axet.androidlibrary.net;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.AssetsDexLoader;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpClientConnection;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.ParseException;
import cz.msebera.android.httpclient.ProtocolException;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.auth.AuthScope;
import cz.msebera.android.httpclient.auth.UsernamePasswordCredentials;
import cz.msebera.android.httpclient.client.CookieStore;
import cz.msebera.android.httpclient.client.CredentialsProvider;
import cz.msebera.android.httpclient.client.RedirectException;
import cz.msebera.android.httpclient.client.config.CookieSpecs;
import cz.msebera.android.httpclient.client.config.RequestConfig;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.AbstractExecutionAwareRequest;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.client.methods.HttpRequestBase;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.client.utils.URIUtils;
import cz.msebera.android.httpclient.conn.ConnectTimeoutException;
import cz.msebera.android.httpclient.conn.ssl.SSLConnectionSocketFactory;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.BasicCredentialsProvider;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.protocol.HttpCoreContext;
import cz.msebera.android.httpclient.protocol.HttpRequestExecutor;
import cz.msebera.android.httpclient.ssl.SSLContexts;
import cz.msebera.android.httpclient.util.Asserts;
import cz.msebera.android.httpclient.util.EntityUtils;

// cz.msebera.android.httpclient recommended by apache
//
// https://hc.apache.org/httpcomponents-client-4.5.x/android-port.html
//
public class HttpClient {
    public static final String TAG = HttpClient.class.getSimpleName();

    public static String USER_AGENT = "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5 Build/MOB30Y) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.81 Mobile Safari/537.36";

    public static final String CONTENTTYPE_HTML = "text/html";
    public static final String CONTENTTYPE_TEXT = "text/plain";
    public static final String CONTENTTYPE_XBITTORRENT = "application/x-bittorrent";
    public static final String CONTENTTYPE_JAVASCRIPT = "application/javascript";
    public static final String CONTENTTYPE_XJAVASCRIPT = "application/x-javascript";
    public static final String CONTENTTYPE_JSON = "application/json";

    public static int CONNECTION_TIMEOUT = 10 * AlarmManager.SEC1;

    public static TrustManager[] TRUST_ALL = new TrustManager[]{new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }
    }};
    public static HostnameVerifier HOST_ALL = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    protected CloseableHttpClient httpclient;
    protected HttpClientContext httpClientContext = HttpClientContext.create();
    protected AbstractExecutionAwareRequest request;
    protected HttpHost proxy;
    protected CredentialsProvider credsProvider;

    public static void init(Context context) { // if you have 'LinearAlloc exceeded capacity' issue
        new SpongyLoader(context, true);
    }

    public static boolean init(ClassLoader l) {
        if (Build.VERSION.SDK_INT <= 19) {
            try {
                Security.insertProviderAt((Provider) l.loadClass("org.spongycastle.jce.provider.BouncyCastleProvider").newInstance(), 1); // new TLS depend on 'AlgorithmParameters EC implementation'
                Security.insertProviderAt((Provider) l.loadClass("org.spongycastle.jsse.provider.BouncyCastleJsseProvider").newInstance(), 1); // install new "TLS" provider
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, TRUST_ALL, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier(HOST_ALL);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "load BouncyCastleProvider", e);
                return false;
            }
        }
        return true;
    }

    public static void init() { // if you do not have 'LinearAlloc exceeded capacity' issue
        init(HttpClient.class.getClassLoader());
    }

    public static void allowRestrictedHeaders() {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public static HttpURLConnection openConnection(Uri uri) {
        return openConnection(uri, null);
    }

    public static HttpURLConnection openConnection(Uri uri, String useragent) {
        return openConnection(uri.toString(), useragent);
    }

    public static HttpURLConnection openConnection(String uri, String useragent) {
        try {
            URL url = new URL(uri);
            return openConnection(0, url, useragent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpURLConnection openConnection(int i, URL url, String useragent) { // handling infinite redirects and ipv6 timeout
        try {
            if (i > 5)
                throw new RuntimeException("Circular redirect exception");
            InetAddress[] aa = InetAddress.getAllByName(url.getHost());
            if (aa.length < 2) {
                return openConnection(i, url, null, useragent);
            } else {
                int k = 0;
                int last = aa.length - 1;
                for (; k < last; k++) {
                    try {
                        InetAddress a = aa[k];
                        return openConnection(i, url, a, useragent);
                    } catch (SocketTimeoutException | SSLPeerUnverifiedException ignore) {
                    }
                }
                InetAddress a = aa[k];
                return openConnection(i, url, a, useragent);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpURLConnection openConnection(int i, URL url, final InetAddress a, String useragent) throws IOException {
        Log.d(TAG, "openConnection: " + url + " (" + a + ")");
        HttpURLConnection conn;
        final URL base = url;
        if (a != null) {
            String auth = a.getHostAddress();
            if (a instanceof Inet6Address)
                auth = "[" + auth + "]";
            url = new URL(Uri.parse(url.toString()).buildUpon().encodedAuthority(auth).build().toString());
        }
        conn = (HttpURLConnection) url.openConnection();
        if (a != null) {
            conn.setRequestProperty("Host", a.getHostName());
            if (conn instanceof HttpsURLConnection) {
                final HttpsURLConnection hs = (HttpsURLConnection) conn;
                final HostnameVerifier old = hs.getHostnameVerifier();
                hs.setHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return old.verify(a.getHostName(), sslSession);
                    }
                });
            }
        }
        conn.setConnectTimeout(HttpClient.CONNECTION_TIMEOUT);
        conn.setReadTimeout(HttpClient.CONNECTION_TIMEOUT);
        conn.setInstanceFollowRedirects(false);
        if (useragent != null)
            conn.setRequestProperty("User-Agent", useragent);
        switch (conn.getResponseCode()) {
            case HttpURLConnection.HTTP_MOVED_PERM:
            case HttpURLConnection.HTTP_MOVED_TEMP:
                String location = conn.getHeaderField("Location");
                location = URLDecoder.decode(location, Charset.defaultCharset().name());
                url = new URL(base, location);  // Deal with relative URLs
                return openConnection(i + 1, url, useragent);

        }
        return conn;
    }

    public static String formatCookie(Cookie c) {
        StringBuilder result = new StringBuilder()
                .append(c.getName())
                .append("=")
                .append("\"")
                .append(c.getValue())
                .append("\"");
        appendAttribute(result, "Path", c.getPath());
        appendAttribute(result, "Domain", c.getDomain());
        return result.toString();
    }

    public static void appendAttribute(StringBuilder builder, String name, String value) {
        if (value != null && builder != null) {
            builder.append("; ");
            builder.append(name);
            builder.append("=\"");
            builder.append(value);
            builder.append("\"");
        }
    }

    public static HttpCookie from(Cookie c) {
        HttpCookie cookie = new HttpCookie(c.getName(), c.getValue());
        cookie.setDomain(c.getDomain());
        cookie.setPath(c.getPath());
        cookie.setSecure(c.isSecure());
        return cookie;
    }

    public static BasicClientCookie from(HttpCookie m) {
        BasicClientCookie b = new BasicClientCookie(m.getName(), m.getValue());
        b.setDomain(m.getDomain());
        b.setPath(m.getPath());
        b.setSecure(m.getSecure());
        return b;
    }

    public static List<NameValuePair> from(Map<String, String> map) {
        List<NameValuePair> nvps = new ArrayList<>();
        for (String key : map.keySet()) {
            String value = map.get(key);
            nvps.add(new BasicNameValuePair(key, value));
        }
        return nvps;
    }

    public static String encode(Map<String, String> map) {
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(from(map));
            InputStream is = entity.getContent();
            return IOUtils.toString(is, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // http://stackoverflow.com/questions/1456987/httpclient-4-how-to-capture-last-redirect-url
    public static String getUrl(HttpClientContext context) {
        Object o = context.getAttribute(HttpCoreContext.HTTP_REQUEST);
        if (o instanceof HttpUriRequest) {
            HttpUriRequest currentReq = (HttpUriRequest) o;
            HttpHost currentHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
            return (currentReq.getURI().isAbsolute()) ? currentReq.getURI().toString() : (currentHost.toURI() + currentReq.getURI());
        }
        return null;
    }

    public static String toStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return e.toString();
    }

    public static SSLConnectionSocketFactory getSSLSocketFactory() {
        SSLContext sc;
        HostnameVerifier host;
        if (Build.VERSION.SDK_INT <= 19) {
            try {
                sc = SSLContext.getInstance("TLS");
                sc.init(null, TRUST_ALL, new SecureRandom());
                host = HOST_ALL;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (KeyManagementException e) {
                throw new RuntimeException(e);
            }
        } else {
            sc = SSLContexts.createDefault();
            host = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        }
        return new SSLConnectionSocketFactory(sc, host);
    }

    public static class SpongyLoader extends AssetsDexLoader.JsonThreadLoader {
        public SpongyLoader(Context context) {
            super(context, "bctls-jdk15on");
        }

        public SpongyLoader(Context context, boolean block) {
            this(context);
            init(block);
        }

        @Override
        public void done(ClassLoader l) {
            if (l == null || !HttpClient.init(l))
                HttpClient.init();
        }
    }

    @TargetApi(11)
    public static class HttpError extends HttpClient.DownloadResponse {
        Throwable e;

        public HttpError(String url, Throwable e) {
            super(CONTENTTYPE_TEXT, Charset.defaultCharset().name(), (InputStream) null, true);
            this.e = e;

            Uri u = Uri.parse(url);

            Throwable t = e;
            while (t.getCause() != null)
                t = t.getCause();

            if (t instanceof ConnectTimeoutException || t instanceof SocketTimeoutException) {
                setTemplate("<h2>Connection Timeout</h2>\n" +
                        "<b>Host:</b> " + u.getHost() + "<br/>\n" +
                        "<b>Message:</b>" + TextUtils.htmlEncode(t.getMessage()));
                return;
            }

            if (t instanceof RedirectException) {
                String a = u.getPath();
                if (u.getQuery() != null) {
                    a += "&" + u.getQuery();
                }
                setTemplate("<h2>Redirect Exception</h2>\n" +
                        "<b>Host:</b> " + u.getHost() + "<br/>\n" +
                        "<b>Url:</b> " + a + "<br/>\n" +
                        "<b>Message:</b> " + TextUtils.htmlEncode(t.getMessage()));
                return;
            }

            setHtml(e);
        }

        public void setHtml(Throwable e) {
            setHtml(toStackTrace(e));
        }

        public void setHtml(String str) {
            setTemplate(TextUtils.htmlEncode(str));
        }

        public void setTemplate(String str) {
            try {
                String html = "<html>";
                html += "<meta name=\"viewport\" content=\"initial-scale=1.0,user-scalable=no,maximum-scale=1,width=device-width\">";
                html += "<body>";
                html += str;
                html += "</body></html>";
                buf = html.getBytes(Charset.defaultCharset().name());
                is = new ByteArrayInputStream(buf);
            } catch (IOException ee) {
                Log.e(TAG, "HttpError", ee);
            }
        }

        @Override
        public String getError() {
            return e.getMessage();
        }

        @Override
        public void downloadText() {
            // ignore
        }

        @Override
        public void download() {
            // ignore
        }

        @Override
        public void attachment() {
            // ignore
        }

        @Override
        public boolean isHtml() {
            return true;
        }
    }

    public static class DownloadResponse {
        public boolean downloaded; // or isAttachment

        public String userAgent;
        public String contentDisposition;
        public long contentLength;
        public String url;
        public byte[] buf;
        public String mimetype;
        public String encoding;
        public InputStream is;

        public HttpUriRequest request;
        public CloseableHttpResponse response;
        public HttpEntity entity;
        public StatusLine status;
        public ContentType contentType;

        public DownloadResponse(HttpClientContext context, HttpUriRequest request, CloseableHttpResponse response) {
            this.request = request;
            this.response = response;
            entity = response.getEntity();
            contentType = ContentType.get(entity);
            if (contentType == null)
                contentType = ContentType.DEFAULT_BINARY;
            url = HttpClient.getUrl(context);
            if (url == null)
                url = request.getURI().toString();
            status = response.getStatusLine();
            mimetype = contentType.getMimeType();
            Header ct = response.getFirstHeader("Content-Disposition");
            if (ct != null)
                contentDisposition = ct.getValue();
            contentLength = entity.getContentLength();
        }

        public DownloadResponse(String mimeType, String encoding, InputStream data, boolean d) {
            this.mimetype = mimeType;
            this.encoding = encoding;
            this.is = data;
            this.downloaded = d;
        }

        public DownloadResponse(String mimeType, String encoding, String data) {
            this(mimeType, encoding, (InputStream) null, true);
            setHtml(data);
        }

        public DownloadResponse(HttpURLConnection conn) throws IOException {
            this.is = new BufferedInputStream(conn.getInputStream());
            this.contentType = ContentType.create(conn.getContentType());
            this.contentLength = conn.getContentLength();
            this.downloaded = true; // connected, BUG!
        }

        public String getUrl() {
            return url;
        }

        public byte[] getBuf() {
            return buf;
        }

        public String getHtml() {
            try {
                return new String(buf, encoding);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public void setHtml(String html) {
            try {
                buf = html.getBytes(encoding);
                is = new ByteArrayInputStream(buf);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public void charset() {
            Charset enc = contentType.getCharset();
            if (isHtml()) {
                if (enc == null) {
                    Document doc = Jsoup.parse(new String(buf, Charset.defaultCharset()));
                    Element e = doc.select("meta[http-equiv=Content-Type]").first();
                    if (e != null) {
                        String content = e.attr("content");
                        try {
                            contentType = ContentType.parse(content);
                            enc = contentType.getCharset();
                        } catch (ParseException ignore) {
                        }
                    } else {
                        e = doc.select("meta[charset]").first();
                        if (e != null) {
                            String content = e.attr("charset");
                            try {
                                enc = Charset.forName(content);
                            } catch (UnsupportedCharsetException ignore) {
                            }
                        }
                    }
                }
                if (enc == null)
                    enc = Charset.defaultCharset();
            }
            if (enc != null)
                encoding = (Charsets.toCharset(enc).name());
        }

        public void download() {
            try {
                if (entity != null) { // old phones it can be null
                    buf = IOUtils.toByteArray(entity.getContent());
                    charset();
                    is = new ByteArrayInputStream(buf);
                    downloaded = true;
                }
                consume();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isAttachment() {
            if (contentDisposition != null)
                return true;
            String[] tt = new String[]{"text", "image", "font", CONTENTTYPE_JAVASCRIPT, CONTENTTYPE_XJAVASCRIPT, CONTENTTYPE_JSON};
            for (String t : tt) {
                if (contentType.getMimeType().startsWith(t))
                    return false;
            }
            return true;
        }

        public void downloadText() {
            if (isAttachment())
                attachment();
            else
                download();
        }

        public void attachment() {
            try {
                if (entity != null) // old phones it can be null
                    contentLength = entity.getContentLength();
                userAgent = USER_AGENT;
                consume();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void consume() throws IOException {
            EntityUtils.consume(entity);
            response.close();
        }

        public String getError() {
            if (status == null) // manually created response
                return null;
            if (status.getStatusCode() != 200) // HTTP OK
                return status.getReasonPhrase();
            return null;
        }

        public boolean isHtml() {
            return mimetype.equals(CONTENTTYPE_HTML);
        }

        public String getContentType() {
            return contentType.toString();
        }

        public InputStream getInputStream() throws IOException {
            if (entity != null)
                return entity.getContent();
            else
                return is;
        }

        public HttpUriRequest getRequest() {
            return request;
        }

        public CloseableHttpResponse getResponse() {
            return response;
        }
    }

    public HttpClient() {
        create();
    }

    public HttpClient(String cookies) {
        create();
        if (cookies != null && !cookies.isEmpty())
            addCookies(cookies);
    }

    public void create() {
        if (credsProvider == null)
            credsProvider = new BasicCredentialsProvider();

        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setUserAgent(USER_AGENT);
        builder.setDefaultRequestConfig(build((RequestConfig) null));
        builder.setRedirectStrategy(new LaxRedirectStrategy() {
            URL absoluteRequestURI;

            @Override
            public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                try {
                    final HttpClientContext clientContext = HttpClientContext.adapt(context);
                    final HttpHost target = clientContext.getTargetHost();
                    Asserts.notNull(target, "Target host");
                    final URI requestURI = new URI(request.getRequestLine().getUri());
                    absoluteRequestURI = URIUtils.rewriteURI(requestURI, target, false).toURL();
                    return super.getLocationURI(request, response, context);
                } catch (URISyntaxException | MalformedURLException ex) {
                    throw new ProtocolException(ex.getMessage(), ex);
                }
            }

            @Override
            protected URI createLocationURI(String location) throws ProtocolException {
                try {
                    return super.createLocationURI(safe(new URL(absoluteRequestURI, location)));
                } catch (MalformedURLException ex) {
                    throw new ProtocolException(ex.getMessage(), ex);
                }
            }
        });
        builder.setDefaultCredentialsProvider(credsProvider);
        builder.setRequestExecutor(new HttpRequestExecutor() {
            @Override
            public HttpResponse execute(HttpRequest request, HttpClientConnection conn, HttpContext context) throws IOException, HttpException {
                return super.execute(request, conn, context);
            }
        });

        // API16<
        // javax.net.ssl.SSLProtocolException: SSL handshake aborted: ssl=0xb89bbee8: Failure in SSL library, usually a protocol error
        // error:14077438:SSL routines:SSL23_GET_SERVER_HELLO:tlsv1 alert internal error (external/openssl/ssl/s23_clnt.c:741 0xaf144a4d:0x00000000)
        // API19<
        // Caused by: javax.net.ssl.SSLException: Connection closed by peer
        // at com.android.org.conscrypt.NativeCrypto.SSL_do_handshake(Native Method)
        // at com.android.org.conscrypt.OpenSSLSocketImpl.startHandshake(OpenSSLSocketImpl.java:405)
        builder.setSSLSocketFactory(getSSLSocketFactory());

        httpclient = build(builder);
    }

    protected CloseableHttpClient build(HttpClientBuilder builder) {
        return builder.build();
    }

    public RequestConfig build(RequestConfig config) {
        RequestConfig.Builder builder;
        if (config == null)
            builder = RequestConfig.custom();
        else
            builder = RequestConfig.copy(config);
        return build(builder);
    }

    public RequestConfig build(RequestConfig.Builder builder) {
        builder.setConnectTimeout(CONNECTION_TIMEOUT);
        builder.setConnectionRequestTimeout(CONNECTION_TIMEOUT);
        builder.setProxy(proxy);
        builder.setCookieSpec(CookieSpecs.STANDARD); // DEFAULT fails with NetscapeDraftSpec.EXPIRES_PATTERN date format
        return builder.build();
    }

    public void setProxy(String host, int port, String scheme) {
        proxy = new HttpHost(host, port, scheme);
    }

    public void setProxy(String host, int port, String scheme, String login, String pass) {
        setProxy(host, port, scheme);
        credsProvider.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(login, pass));
    }

    public void clearProxy() {
        proxy = null;
    }

    public void addCookies(String url, String cookies) {
        Uri u = Uri.parse(url);
        CookieStore s = getCookieStore();
        List<HttpCookie> cc = HttpCookie.parse(cookies);
        for (HttpCookie c : cc) {
            BasicClientCookie m = from(c);
            if (m.getDomain() == null)
                m.setDomain(u.getAuthority());
            removeCookie(m);
            s.addCookie(m);
        }
    }

    public void addCookies(String cookies) {
        CookieStore cs = getCookieStore();
        String[] ss = cookies.split("\n");
        for (String s : ss) {
            List<HttpCookie> cc = HttpCookie.parse(s);
            for (HttpCookie c : cc) {
                BasicClientCookie m = from(c);
                removeCookie(m);
                cs.addCookie(m);
            }
        }
    }

    public void removeCookie(HttpCookie m) {
        removeCookie(from(m));
    }

    public void removeCookie(BasicClientCookie m) {
        try {
            CookieStore s = getCookieStore();
            BasicClientCookie rm = (BasicClientCookie) m.clone();
            rm.setExpiryDate(new Date(0));
            s.addCookie(rm);
        } catch (CloneNotSupportedException e) {
        }
    }

    public HttpCookie getCookie(int i) {
        CookieStore s = getCookieStore();
        Cookie c = s.getCookies().get(i);
        return from(c);
    }

    public void addCookie(HttpCookie c) {
        CookieStore s = getCookieStore();
        s.addCookie(from(c));
    }

    public int getCount() {
        CookieStore s = getCookieStore();
        return s.getCookies().size();
    }

    public String getCookies() {
        String str = "";

        CookieStore s = getCookieStore();
        List<Cookie> list = s.getCookies();
        for (int i = 0; i < list.size(); i++) {
            Cookie c = list.get(i);

            String result = formatCookie(c);

            if (!str.isEmpty())
                str += "\n";
            str += "Set-Cookie: ";
            str += result;
        }
        return str;
    }

    public void clearCookies() {
        httpClientContext.setCookieStore(new BasicCookieStore());
    }

    public CookieStore getCookieStore() {
        CookieStore apacheStore = httpClientContext.getCookieStore();
        if (apacheStore == null) {
            apacheStore = new BasicCookieStore();
            httpClientContext.setCookieStore(apacheStore);
        }
        return apacheStore;
    }

    public void setCookieStore(CookieStore store) {
        httpClientContext.setCookieStore(store);
    }

    public AbstractExecutionAwareRequest getRequest() {
        return request;
    }

    public void abort() {
        if (request != null) {
            request.abort();
            request = null;
        }
    }

    public CloseableHttpResponse execute(String base, HttpRequestBase request) {
        this.request = request;

        if (proxy != null)
            request.setConfig(build(request.getConfig()));

        if (base != null) {
            if (!base.equals(request.getURI().toString()))
                request.addHeader("Referer", base);
            Uri u = Uri.parse(base);
            request.addHeader("Origin", new Uri.Builder().scheme(u.getScheme()).authority(u.getAuthority()).toString());
        }

        return execute(request);
    }

    public CloseableHttpResponse execute(HttpRequestBase request) {
        try {
            return httpclient.execute(request, httpClientContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String get(String base, String url) {
        try {
            DownloadResponse w = getResponse(base, url);
            w.download();
            return w.getHtml();
        } finally {
            request = null;
        }
    }

    public byte[] getBytes(String base, String url) {
        try {
            DownloadResponse w = getResponse(base, url);
            w.download();
            return w.getBuf();
        } finally {
            request = null;
        }
    }

    public static String safe(String url) { // deal with java.lang.IllegalArgumentException: Illegal character in path at index, also https://trac.nginx.org/nginx/ticket/196
        try {
            URL u = new URL(url);
            return safe(u);
        } catch (MalformedURLException e) {
            return url;
        }
    }

    public static String safe(URL u) {
        try {
            String p = u.getPath();
            if (p != null) {
                try {
                    p = URLDecoder.decode(p, Charset.defaultCharset().name()); // URI need decoded string
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
            String q = u.getQuery();
            if (q != null) {
                try {
                    q = URLDecoder.decode(q, Charset.defaultCharset().name()); // URI need decoded string
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
            URI uri = new URI(u.getProtocol(), u.getUserInfo(), u.getHost(), u.getPort(), p, q, u.getRef());
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public DownloadResponse getResponse(String base, String url) {
        HttpGet httpGet = new HttpGet(safe(url));
        return getResponse(base, httpGet);
    }

    public DownloadResponse getResponse(String base, HttpGet httpGet) {
        try {
            CloseableHttpResponse response = execute(base, httpGet);
            return new DownloadResponse(httpClientContext, httpGet, response);
        } finally {
            request = null;
        }
    }

    public String post(String base, String url, String[][] map) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < map.length; i++)
            m.put(map[i][0], map[i][1]);
        return post(base, url, m);
    }

    public String post(String base, String url, Map<String, String> map) {
        return post(base, url, from(map));
    }

    public String post(String base, String url, List<NameValuePair> nvps) {
        try {
            DownloadResponse w = postResponse(base, url, nvps);
            w.download();
            return w.getHtml();
        } finally {
            request = null;
        }
    }

    public DownloadResponse postResponse(String base, String url, Map<String, String> map) {
        return postResponse(base, url, from(map));
    }

    public DownloadResponse postResponse(String base, String url, List<NameValuePair> nvps) {
        try {
            HttpPost httpPost = new HttpPost(safe(url));
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, Charset.defaultCharset()));
            CloseableHttpResponse response = execute(base, httpPost);
            return new DownloadResponse(httpClientContext, httpPost, response);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            request = null;
        }
    }

    public DownloadResponse postResponse(String base, String url, HttpPost httpPost) {
        try {
            CloseableHttpResponse response = execute(base, httpPost);
            return new DownloadResponse(httpClientContext, httpPost, response);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            request = null;
        }
    }

    public DownloadResponse executeResponse(String base, HttpRequestBase r) {
        try {
            CloseableHttpResponse response = execute(base, r);
            return new DownloadResponse(httpClientContext, r, response);
        } finally {
            request = null;
        }
    }
}
