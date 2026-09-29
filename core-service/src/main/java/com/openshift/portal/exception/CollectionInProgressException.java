package com.openshift.portal.exception;

/** A collection was requested while another one holds the collection lock. */
public class CollectionInProgressException extends RuntimeException {
    public CollectionInProgressException(String message) {
        super(message);
    }
}
