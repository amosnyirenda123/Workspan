package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** HTTP 401: the caller is not signed in or the credentials are wrong. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(ErrorCode code) {
        super(expect(code, HttpStatus.UNAUTHORIZED));
    }

    public UnauthorizedException(ErrorCode code, Map<String, ?> params) {
        super(expect(code, HttpStatus.UNAUTHORIZED), params);
    }
}
