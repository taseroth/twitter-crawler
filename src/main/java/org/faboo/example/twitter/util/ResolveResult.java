package org.faboo.example.twitter.util;

import java.net.MalformedURLException;

public class ResolveResult {

    private final boolean resolved;
    private final String url;
    private final RedirectResolver.ResolveError error;
    private final String hostName;

    static ResolveResult resolved(String finalUrl) {
        try {
            return new ResolveResult(true, finalUrl, HostResolver.getCleanedHost(finalUrl), null);
        } catch (MalformedURLException e) {
            return new ResolveResult(false, finalUrl, null, new RedirectResolver.ResolveError(-3, e.getMessage()));
        }
    }

    static ResolveResult moved(String lastUrl, String nextUrl) {

        try {
            return new ResolveResult(false, HostResolver.buildAbsoluteUrl(lastUrl, nextUrl), null, null);
        } catch (MalformedURLException e) {
            return new ResolveResult(false, lastUrl, null, new RedirectResolver.ResolveError(-4, e.getMessage()));
        }
    }

    static ResolveResult error(String url, RedirectResolver.ResolveError error) {
        return new ResolveResult(false, url, null, error);
    }

    private ResolveResult(boolean resolved, String url, String hostName, RedirectResolver.ResolveError error) {
        this.resolved = resolved;
        this.url = url;
        this.error = error;
        this.hostName = hostName;
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean isError() {
        return null != error;
    }

    public String getUrl() {
        return url;
    }

    public RedirectResolver.ResolveError getError() {
        return error;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public String toString() {
        return "ResolveStatus{" +
                "resolved=" + resolved +
                ", url='" + url + '\'' +
                '}';
    }
}
