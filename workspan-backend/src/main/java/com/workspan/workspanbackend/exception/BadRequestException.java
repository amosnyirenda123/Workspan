package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** HTTP 400: the request breaks a business rule or carries invalid values. */
public class BadRequestException extends ApiException {
    public BadRequestException(ErrorCode code) {
        super(expect(code, HttpStatus.BAD_REQUEST));
    }

    public BadRequestException(ErrorCode code, Map<String, ?> params) {
        super(expect(code, HttpStatus.BAD_REQUEST), params);
    }
}
