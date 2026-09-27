package com.openshift.portal.exception;

public class AcmConnectionException extends RuntimeException {
    public AcmConnectionException(String message) {
        super(message);
    }

    public AcmConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
