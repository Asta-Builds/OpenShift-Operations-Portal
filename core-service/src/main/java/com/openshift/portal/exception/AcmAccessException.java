package com.openshift.portal.exception;

/**
 * The hub refused the portal (401/403) or its credentials are missing. Retrying cannot help, so this is never retried.
 */
public class AcmAccessException extends RuntimeException {
    public AcmAccessException(String message) {
        super(message);
    }

    public AcmAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
