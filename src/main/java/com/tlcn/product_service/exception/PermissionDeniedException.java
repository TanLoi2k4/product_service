package com.tlcn.product_service.exception;

import org.springframework.http.HttpStatus;

public class PermissionDeniedException extends BusinessException {
    public PermissionDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }

    public PermissionDeniedException(String message, String errorCode) {
        super(message, HttpStatus.FORBIDDEN, errorCode);
    }
}
