package org.faboo.example.twitter.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * Resolver that follows http redirects to the end and report the final url.
 */
public class RedirectResolver {

    private final static Logger log = LoggerFactory.getLogger(RedirectResolver.class);

    private final CloseableHttpClient httpClient;
    private final RequestConfig requestConfig;


    public RedirectResolver(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
        this.requestConfig = RequestConfig.custom()
                .setSocketTimeout(30000)
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(60000)
                .setRedirectsEnabled(false)
                .build();
    }

    public ResolveResult resolve(String url) {

        log.trace("resolving {}", url);

        try {
            int hopsRemaining = 10;
            String nextUrl = url;
            do {
                ResolveResult resolveStatus = resolveOnce(nextUrl);
                if (resolveStatus.isResolved()) {

                    return resolveStatus;
                }
                hopsRemaining--;
                nextUrl = resolveStatus.getUrl();
            } while (hopsRemaining > 0);
        } catch (ResolveException e) {
            return ResolveResult.error(url, e.getError());
        }
        return ResolveResult.error(url, new ResolveError(-2, "to many redirects"));
    }


    private ResolveResult resolveOnce(String urlString) throws ResolveException {

        HttpHead head;
        head = new HttpHead(parseToUri(urlString));
        head.setConfig(requestConfig);

        try (CloseableHttpResponse response = httpClient.execute(head)) {

            int status = response.getStatusLine().getStatusCode();
            if (status == HttpURLConnection.HTTP_OK) {
                if (urlString.length()>1000) {
                    throw new ResolveException(new ResolveError(-2, "url to long:" + urlString.length()));
                }
                return ResolveResult.resolved(urlString);
            }

            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {

                if (response.getLastHeader("Location") != null) {
                    return ResolveResult.moved(urlString, response.getLastHeader("Location").getValue());
                } else {
                    log.error("{} returns  {}, but no location", urlString, status);
                }
            }
            throw new ResolveException(new ResolveError(status, response.getStatusLine().getReasonPhrase()));
        } catch (UnknownHostException e) {
            throw new ResolveException(new ResolveError(-6, findRootError(e)));
        }
        catch (IOException e) {
            log.trace("error resolving " + urlString, e);
            throw new ResolveException(new ResolveError(-1, findRootError(e)));
        }
    }

    private URI parseToUri(String urlString) throws ResolveException {

        try {
            URL url = new URL(urlString);
            return  new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(),
                    url.getQuery(), url.getRef());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new ResolveException(new ResolveError(-7, findRootError(e)));
        }

    }

    private String findRootError(Exception e) {

        String message;
        Throwable next = e;
        do {
            message = next.getMessage();
            next = next.getCause();
        } while (message == null);

        return message;
    }

    public static class ResolveException extends Exception {

        private final ResolveError error;

        ResolveException(ResolveError error) {
            this.error = error;
        }

        ResolveError getError() {
            return error;
        }
    }

    public static class ResolveError {
        private final int status;
        private final String message;

        ResolveError(int status, String httpMessage) {
            this.status = status;
            this.message = httpMessage;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ResolveError{" +
                    "status=" + status +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

}
