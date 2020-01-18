package org.faboo.example.twitter.service;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.faboo.example.twitter.util.RedirectResolver;
import org.faboo.example.twitter.util.ResolveResult;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

public class ForkJoinResolver {

    CloseableHttpClient httpClient;
    private final ForkJoinPool pool;

    public ForkJoinResolver() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(50);
        connectionManager.setMaxTotal(500);

        SSLContext sslContext = SSLContextBuilder
                .create()
                .loadTrustMaterial(new TrustSelfSignedStrategy())
                .build();
        SSLConnectionSocketFactory connectionFactory =
                new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        httpClient = HttpClients.custom()
                .disableCookieManagement()
                .setConnectionManager(connectionManager)
                .disableCookieManagement()
                .setSSLSocketFactory(connectionFactory)
                .disableRedirectHandling().build();

        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 10);
    }

    public Map<String,ResolveResult> resolve(Collection<String> links) {

        ForkJoinTask<Map<String,ResolveResult>> starter = pool.submit(new RecursiveTask<>() {
            @Override
            protected Map<String,ResolveResult> compute() {

                Set<ResolverTask> tasks = new HashSet<>();

                links.forEach(link -> {
                    ResolverTask task = new ResolverTask(link);
                    task.fork();
                    tasks.add(task);
                });
                Map<String,ResolveResult> results = new HashMap<>();
                tasks.forEach(task -> {
                    Map.Entry<String, ResolveResult> result = task.join();
                    results.put(result.getKey(), result.getValue());
                });
                return results;
            }
        });
        return starter.join();
    }



    private class ResolverTask extends RecursiveTask<Map.Entry<String,ResolveResult>> {

        private final String link;

        private final RedirectResolver resolver = new RedirectResolver(httpClient);

        private ResolverTask(String link) {
            this.link = link;
        }

        @Override
        protected Map.Entry<String,ResolveResult> compute() {
            return Map.entry(link, resolver.resolve(link));
        }
    }
}
