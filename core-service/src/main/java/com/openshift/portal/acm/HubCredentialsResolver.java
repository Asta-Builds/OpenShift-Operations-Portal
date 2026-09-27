package com.openshift.portal.acm;

import com.openshift.portal.config.AcmProperties;
import com.openshift.portal.domain.entity.AcmHub;
import com.openshift.portal.exception.AcmAccessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a hub's credentials from its Kubernetes Secret, mounted as a directory named after
 * {@code credentials_secret_ref} (keys {@code token} and optional {@code ca.crt}). Nothing is cached, so a
 * rotated Secret takes effect on the next collection.
 */
@Component
@RequiredArgsConstructor
public class HubCredentialsResolver {

    private final AcmProperties properties;

    public HubCredentials resolve(AcmHub hub) {
        String ref = hub.getCredentialsSecretRef();
        if (ref == null || ref.isBlank()) {
            throw new AcmAccessException("ACM Hub " + hub.getName() + " has no credentials Secret reference");
        }
        Path secretDir = Path.of(properties.getAcm().getCredentialsDir()).resolve(ref).normalize();
        if (!secretDir.startsWith(Path.of(properties.getAcm().getCredentialsDir()).normalize())) {
            throw new AcmAccessException("Invalid credentials Secret reference for ACM Hub " + hub.getName());
        }
        try {
            String token = Files.readString(secretDir.resolve("token")).trim();
            Path caCert = secretDir.resolve("ca.crt");
            return new HubCredentials(token, Files.exists(caCert) ? caCert : null);
        } catch (IOException e) {
            throw new AcmAccessException("Cannot read the token of ACM Hub " + hub.getName() + " from " + secretDir, e);
        }
    }

    /** {@code caCertificate} is null when the hub's certificate is signed by a CA the JVM already trusts. */
    public record HubCredentials(String token, Path caCertificate) {
    }
}
