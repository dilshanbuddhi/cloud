package com.ijse.eca.orders.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> {
                    if (err instanceof FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return err.getDefaultMessage();
                })
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, msg, request.getRequestURI());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unknown(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        String msg = unwrapMessage(ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, msg, request.getRequestURI());
    }

    /**
     * Surfaces Firestore/gRPC causes (permissions, wrong project, disabled API) instead of a generic
     * "Unexpected error" — especially on GCP VM where ADC / project id often differ from localhost.
     */
    private static String unwrapMessage(Exception ex) {
        Throwable t = ex;
        int depth = 0;
        while (t != null && depth++ < 8) {
            String m = t.getMessage();
            if (StringUtils.hasText(m)) {
                if (m.length() > 600) {
                    return m.substring(0, 600) + "...";
                }
                return m;
            }
            t = t.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, String path) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(body);
    }
}
