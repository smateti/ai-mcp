package com.example.gitlabmigration.client;

import com.example.gitlabmigration.config.GitlabInstanceProperties;
import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.config.SslProperties;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Factory that builds RestClient instances with:
 * - Custom SSL truststore for HTTPS GitLab endpoints
 * - PRIVATE-TOKEN default header
 * - Automatic retry with exponential backoff on 429/5xx
 */
@Component
public class GitlabRestClientFactory {

    private static final Logger log = LoggerFactory.getLogger(GitlabRestClientFactory.class);

    private final MigrationProperties props;
    private final HttpClientConnectionManager connectionManager;

    public GitlabRestClientFactory(MigrationProperties props) {
        this.props = props;
        this.connectionManager = buildConnectionManager();
    }

    /**
     * Create a RestClient for a specific GitLab instance.
     */
    public RestClient createClient(GitlabInstanceProperties instance) {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true) // shared across source + destination clients
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(60, TimeUnit.SECONDS))
                        .setConnectionRequestTimeout(Timeout.of(10, TimeUnit.SECONDS))
                        .build())
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("PRIVATE-TOKEN", instance.getToken())
                .requestInterceptor(new RetryInterceptor())
                .build();
    }

    /** Build source-instance RestClient. */
    public RestClient createSourceClient() {
        return createClient(props.getSource());
    }

    /** Build destination-instance RestClient. */
    public RestClient createDestinationClient() {
        return createClient(props.getDestination());
    }

    private HttpClientConnectionManager buildConnectionManager() {
        SslProperties ssl = props.getSsl();
        String truststorePath = ssl.getTruststorePath();

        if (truststorePath != null && !truststorePath.isBlank()) {
            try {
                KeyStore trustStore = KeyStore.getInstance(ssl.getTruststoreType());
                try (FileInputStream fis = new FileInputStream(new File(truststorePath))) {
                    trustStore.load(fis, ssl.getTruststorePassword().toCharArray());
                }

                SSLContext sslContext = org.apache.hc.core5.ssl.SSLContexts.custom()
                        .loadTrustMaterial(trustStore, null)
                        .build();

                log.info("SSL: loaded custom truststore from {} (type={}, skipHostnameVerification={})",
                        truststorePath, ssl.getTruststoreType(), ssl.isSkipHostnameVerification());

                SSLConnectionSocketFactoryBuilder sslSocketBuilder = SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext);
                if (ssl.isSkipHostnameVerification()) {
                    sslSocketBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                }

                return PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketBuilder.build())
                        .setMaxConnTotal(50)
                        .setMaxConnPerRoute(20)
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to load SSL truststore from " + truststorePath, e);
            }
        }

        log.info("SSL: using JVM default trust (no custom truststore configured)");
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(50)
                .setMaxConnPerRoute(20)
                .build();
    }

    /**
     * Retry interceptor matching Python's urllib3 Retry policy:
     * 5 attempts, exponential backoff (2s, 4s, 8s, 16s, 32s),
     * retries on 429/500/502/503/504, respects Retry-After header.
     */
    static class RetryInterceptor implements ClientHttpRequestInterceptor {

        private static final int MAX_ATTEMPTS = 5;
        private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);
        private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                             ClientHttpRequestExecution execution) throws IOException {
            int attempt = 0;
            while (true) {
                attempt++;
                ClientHttpResponse response;
                try {
                    response = execution.execute(request, body);
                } catch (IOException e) {
                    if (attempt >= MAX_ATTEMPTS) throw e;
                    long backoffMs = computeBackoff(attempt);
                    log.warn("Request to {} failed (attempt {}/{}): {}. Retrying in {}ms",
                            request.getURI(), attempt, MAX_ATTEMPTS, e.getMessage(), backoffMs);
                    sleep(backoffMs);
                    continue;
                }

                HttpStatusCode statusCode = response.getStatusCode();
                if (!RETRYABLE_STATUSES.contains(statusCode.value()) || attempt >= MAX_ATTEMPTS) {
                    return response;
                }

                long backoffMs = parseRetryAfterOrDefault(response.getHeaders(), attempt);
                log.warn("HTTP {} from {} (attempt {}/{}). Retrying in {}ms",
                        statusCode.value(), request.getURI(), attempt, MAX_ATTEMPTS, backoffMs);
                response.close();
                sleep(backoffMs);
            }
        }

        private long parseRetryAfterOrDefault(HttpHeaders headers, int attempt) {
            String retryAfter = headers.getFirst("Retry-After");
            if (retryAfter != null) {
                try {
                    return Long.parseLong(retryAfter) * 1000;
                } catch (NumberFormatException ignored) {
                    // Ignore non-numeric Retry-After
                }
            }
            return computeBackoff(attempt);
        }

        private long computeBackoff(int attempt) {
            // backoff_factor=2: 2^1=2s, 2^2=4s, 2^3=8s, 2^4=16s, 2^5=32s
            return (long) Math.pow(2, attempt) * 1000;
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", e);
            }
        }
    }
}
