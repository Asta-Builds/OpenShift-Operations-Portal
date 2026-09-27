package com.openshift.portal.exception;

import lombok.Getter;

import java.util.List;

/** An inventory file was rejected; {@code errors} lists what to fix, by line. */
@Getter
public class InventoryImportException extends RuntimeException {

    private final List<String> errors;

    public InventoryImportException(List<String> errors) {
        super("The inventory file was rejected: " + errors.size() + " problem(s)");
        this.errors = List.copyOf(errors);
    }
}
