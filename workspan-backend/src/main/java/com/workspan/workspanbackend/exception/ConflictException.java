package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** HTTP 409: the request conflicts with the current state of the data. */
public class ConflictException extends ApiException {
    public ConflictException(ErrorCode code) {
        super(expect(code, HttpStatus.CONFLICT));
    }

    public ConflictException(ErrorCode code, Map<String, ?> params) {
        super(expect(code, HttpStatus.CONFLICT), params);
    }
}
