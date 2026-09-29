package com.openshift.portal.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(AcmConnectionException.class)
    public ResponseEntity<Map<String, Object>> handleAcmConnection(AcmConnectionException ex) {
        return error(HttpStatus.BAD_GATEWAY, "ACM Hub Communication Failure", ex.getMessage());
    }

    @ExceptionHandler(CollectionInProgressException.class)
    public ResponseEntity<Map<String, Object>> handleCollectionInProgress(CollectionInProgressException ex) {
        return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(InventoryImportException.class)
    public ResponseEntity<Map<String, Object>> handleInventoryImport(InventoryImportException ex) {
        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage());
        body.put("errors", ex.getErrors());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", "Access denied.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Details stay in the server log; the client only learns that the request failed
        log.error("Unhandled exception while processing request", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.");
    }

    /** Names each invalid field, so a form can show what to fix; the messages hold no internal details. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ex.getBody();
        if (!fields.isEmpty()) {
            problem.setDetail("Invalid request content: " + fields);
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Standard Spring MVC exceptions (malformed parameters, unknown paths, unsupported methods, unreadable bodies)
     * keep their 4xx status and client-safe detail instead of falling through to the 500 handler.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail
                : ex instanceof ErrorResponse errorResponse ? errorResponse.getBody() : null;
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = problem != null && problem.getDetail() != null ? problem.getDetail() : error;
        return super.handleExceptionInternal(ex, errorBody(statusCode.value(), error, message), headers, statusCode, request);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(errorBody(status.value(), error, message));
    }

    private static Map<String, Object> errorBody(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
