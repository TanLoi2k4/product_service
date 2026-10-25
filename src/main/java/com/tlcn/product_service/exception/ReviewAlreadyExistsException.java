package com.tlcn.product_service.exception;

import org.springframework.http.HttpStatus;

public class ReviewAlreadyExistsException extends BusinessException {
    public ReviewAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ReviewAlreadyExistsException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}
