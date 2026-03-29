package com.github.axet.androidlibrary.app;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Ping {
    public String host;
    public boolean ipv6;
    public int timeout = 5000;
    public int ttl; // set ttl

    public String ip; // host resolved ip
    public String hoop; // hoop or destination name
    public String hoopIp; // hoop (or destination) ip
    public long ms; // ping delay
    public boolean ttlex; // ttl exceeded
    public int ttlr; // ttl recived (host pinged)
    public int seq; // icmp_seq=387

    public boolean reachable; // host reachable

    public static InetAddress getHostByName(boolean ipv6, String host) throws UnknownHostException {
        InetAddress[] aa = InetAddress.getAllByName(host);
        for (InetAddress a : aa) {
            if (a instanceof Inet4Address && !ipv6)
                return a;
            if (a instanceof Inet6Address && ipv6)
                return a;
        }
        return null;
    }

    public Ping() {
    }

    public Ping(String host, boolean ipv6) {
        this.host = host;
        this.ipv6 = ipv6;
    }

    public Ping(String host, boolean ipv6, int ttl) {
        this.host = host;
        this.ipv6 = ipv6;
        this.ttl = ttl;
    }

    public boolean ping() {
        try {
            InetAddress address = PingExt.getHostByName(ipv6, host);
            if (address == null)
                throw new RuntimeException("Unknown host: " + host);
            ip = address.getHostAddress();
            long now = System.currentTimeMillis();
            if (ttl != 0) {
                boolean r = address.isReachable(null, ttl, timeout);
                ms = System.currentTimeMillis() - now;
                reachable = ms < timeout;
                ttlex = !r;
                hoopIp = host;
            } else {
                reachable = address.isReachable(timeout); // return true even if host is "Destination is Unreachable"
                ms = System.currentTimeMillis() - now;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public boolean fail() {
        return !reachable;
    }
}
