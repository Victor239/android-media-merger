package com.github.axet.androidlibrary.app;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PingExt extends Ping {

    public static Pattern HEAD = Pattern.compile("PING ([^ ()]*)[ ]*\\(([^ ]*)\\) ([0-9]*)"); // PING google.com (64.233.161.102) 56(84) bytes of data.
    public static Pattern BYTES_IP4 = Pattern.compile(".* bytes from ([^ :]*): "); // 64 bytes from 1.1.1.1: seq=0 ttl=56 time=36.380 ms
    public static Pattern BYTES_IP6 = Pattern.compile(".* bytes from ([^ ]*): "); // 64 bytes from 1.1.1.1: seq=0 ttl=56 time=36.380 ms
    public static Pattern BYTES_NAME = Pattern.compile(".* bytes from ([^ ]*) \\(([^ ]*)\\)"); // 64 bytes from axx01.distributed.zenon.net (213.189.197.1): icmp_seq=1 ttl=49 time=43.1 ms
    public static Pattern FROM_NAME = Pattern.compile("From ([^ ]*) \\(([^ ]*)\\)"); // From OpenWrt.lan (10.10.5.1): icmp_seq=1 Time to live exceeded
    public static Pattern FROM_IP4 = Pattern.compile("From ([^ :]*): "); // From 10.173.0.110: icmp_seq=1 Time to live exceeded
    public static Pattern FROM_IP6 = Pattern.compile("From ([0-9a-zA-Z:.]*)"); // From 2a06:a003:907b:1::1 icmp_seq=1 Time exceeded: Hop limit
    public static Pattern TTLVAL = Pattern.compile("ttl=(\\d+)");
    public static Pattern ICMPSEQ = Pattern.compile("icmp_seq=(\\d+)");

    public String size; // 56(84) bytes of data

    public int res;
    public ProcessBuilder ping;

    public String err;
    public String out;

    public static String readLine(InputStream is) {
        try {
            StringBuilder str = new StringBuilder();
            int i;
            while ((i = is.read()) != -1) {
                if (i == '\n')
                    break;
                str.append((char) i);
            }
            return str.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean any(String s, String... aa) {
        for (String a : aa) {
            if (a.contains(s))
                return true;
        }
        return false;
    }

    public PingExt(String host, boolean ipv6, int ttl) {
        this.ipv6 = ipv6;
        this.host = host;
        String cmd = ipv6 ? "ping6" : "ping";
        resolve(ipv6, host);
        ping = new ProcessBuilder(cmd, "-W", "" + timeout / 1000, "-c", "1", "-t", "" + ttl, host);
    }

    public PingExt(String host, boolean ipv6) {
        this.host = host;
        String cmd = ipv6 ? "ping6" : "ping";
        resolve(ipv6, host);
        ping = new ProcessBuilder(cmd, "-W", "" + timeout / 1000, "-c", "1", host);
    }

    public void resolve(boolean ipv6, String host) {
        try {
            InetAddress address = Ping.getHostByName(ipv6, host);
            if (address != null)
                ip = address.getHostAddress();
        } catch (UnknownHostException ignore) {
        }
    }

    public void resolve(String cmd) {
        try {
            ProcessBuilder ping = new ProcessBuilder(cmd, "-W", "" + 1, "-c", "1", host);
            Process pp = ping.start();
            String out = readLine(pp.getInputStream());
            head(out, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void head(String out, String err) {
        if (any("Name or service not known", err, out) || any("bad address", out, err) || any("unknown host", err, out))
            throw new RuntimeException(String.format("Unknown host %s", host));  // ping: unknown host a.com
        if (any("Network is unreachable", err, out))
            throw new RuntimeException("Network is unreachable"); // connect: Network is unreachable
        Matcher m = HEAD.matcher(out);
        if (m.find()) {
            host = m.group(1);
            ip = m.group(2);
            size = m.group(3);
        }
    }

    public boolean ping() {
        try {
            long now = System.currentTimeMillis();
            Process pp = ping.start();
            res = pp.waitFor();
            ms = System.currentTimeMillis() - now;
            err = IOUtils.toString(pp.getErrorStream(), Charset.defaultCharset());
            out = IOUtils.toString(pp.getInputStream(), Charset.defaultCharset());
            head(out, err);
            ttlex = out.contains("Time to live exceeded") || out.contains("Time exceeded: Hop limit"); // From 10.173.0.110: icmp_seq=1 Time to live exceeded || From 2a06:a003:907b:1::1 icmp_seq=1 Time exceeded: Hop limit
            Matcher m = FROM_NAME.matcher(out);
            if (m.find()) {
                hoop = m.group(1);
                hoopIp = m.group(2);
            }
            m = BYTES_IP4.matcher(out);
            if (m.find())
                hoopIp = m.group(1);
            else {
                m = BYTES_IP6.matcher(out);
                if (m.find())
                    hoopIp = m.group(1);
            }
            m = BYTES_NAME.matcher(out);
            if (m.find()) {
                hoop = m.group(1);
                hoopIp = m.group(2);
            }
            m = FROM_IP4.matcher(out);
            if (m.find())
                hoopIp = m.group(1);
            if (hoopIp == null) { // ignore ipv4 'From 10.10.5.55: icmp_seq=1 Destination Host Unreachable'
                m = FROM_IP6.matcher(out); // looking for 'From 2001:470:28:187::1 icmp_seq=1 Time exceeded: Hop limit'
                if (m.find())
                    hoopIp = m.group(1);
            }
            m = TTLVAL.matcher(out);
            if (m.find()) {
                try {
                    ttlr = Integer.parseInt(m.group(1));
                } catch (Exception ignore) {
                }
            }
            m = ICMPSEQ.matcher(out);
            if (m.find()) {
                try {
                    seq = Integer.parseInt(m.group(1));
                } catch (Exception ignore) {
                }
            }
            reachable = !out.contains("Destination Host Unreachable") && !out.contains("Destination Port Unreachable"); // From 10.10.5.51 icmp_seq=1 Destination Host Unreachable
            return true;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean fail() { // !(host reachable or ttl exceeded)
        if (!reachable)
            return true;
        if (res != 0 && !ttlex)
            return true;
        return false;
    }
}
