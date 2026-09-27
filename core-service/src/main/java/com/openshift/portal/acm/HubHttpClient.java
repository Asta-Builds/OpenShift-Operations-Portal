package com.openshift.portal.acm;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.exception.AcmAccessException;
import com.openshift.portal.exception.AcmConnectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;

/**
 * Sends requests to a hub's HTTP endpoints (Observability, Search) with the hub's token and CA, and maps failures
 * the same way for every endpoint: 401/403 are not retryable, 5xx and transport errors are.
 */
@Component
@RequiredArgsConstructor
public class HubHttpClient {

    private final AcmProperties properties;

    /** Adds the bearer token and read timeout to {@code request} and returns the body of a 200 answer. */
    public String send(HttpRequest.Builder request, HubCredentialsResolver.HubCredentials credentials, String endpoint) {
        HttpRequest built = request
                .header("Authorization", "Bearer " + credentials.token())
                .timeout(Duration.ofMillis(properties.getCollector().getReadTimeoutMs()))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient(credentials).send(built, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AcmConnectionException(endpoint + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AcmConnectionException(endpoint + " request was interrupted", e);
        }

        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new AcmAccessException(endpoint + " refused the token (HTTP " + status + ")");
        }
        if (status >= 500) {
            throw new AcmConnectionException(endpoint + " answered HTTP " + status);
        }
        if (status != 200) {
            throw new IllegalStateException(endpoint + " rejected the request (HTTP " + status + ")");
        }
        return response.body();
    }

    private HttpClient httpClient(HubCredentialsResolver.HubCredentials credentials) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getCollector().getConnectTimeoutMs()));
        if (credentials.caCertificate() != null) {
            builder.sslContext(trusting(credentials.caCertificate()));
        }
        return builder.build();
    }

    /** Trusts exactly the CA certificates in the hub Secret's {@code ca.crt}. */
    private static SSLContext trusting(Path caCertificate) {
        try (InputStream in = Files.newInputStream(caCertificate)) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                trustStore.setCertificateEntry("hub-ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new AcmAccessException("Cannot load the hub CA certificate " + caCertificate, e);
        }
    }
}
