package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** HTTP 403: the caller is signed in but not allowed to do this. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(ErrorCode code) {
        super(expect(code, HttpStatus.FORBIDDEN));
    }

    public ForbiddenException(ErrorCode code, Map<String, ?> params) {
        super(expect(code, HttpStatus.FORBIDDEN), params);
    }
}
