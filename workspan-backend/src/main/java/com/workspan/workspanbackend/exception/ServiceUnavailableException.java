package com.workspan.workspanbackend.exception;

import org.springframework.http.HttpStatus;

/** HTTP 503: a dependency such as the mail server failed; the client may retry later. */
public class ServiceUnavailableException extends ApiException {
    public ServiceUnavailableException(ErrorCode code, Throwable cause) {
        super(expect(code, HttpStatus.SERVICE_UNAVAILABLE), java.util.Map.of(), java.util.List.of(), cause);
    }
}
