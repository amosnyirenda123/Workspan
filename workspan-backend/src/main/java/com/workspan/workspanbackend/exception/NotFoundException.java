package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** HTTP 404: the item does not exist or is not visible to the caller. */
public class NotFoundException extends ApiException {
    public NotFoundException(ErrorCode code) {
        super(expect(code, HttpStatus.NOT_FOUND));
    }

    public NotFoundException(ErrorCode code, Map<String, ?> params) {
        super(expect(code, HttpStatus.NOT_FOUND), params);
    }
}
