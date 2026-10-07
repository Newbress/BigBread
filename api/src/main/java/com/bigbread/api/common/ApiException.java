package com.bigbread.api.common;

import org.springframework.http.HttpStatus;

/** HTTP 상태를 함께 가지는 API 예외. {@link ApiExceptionHandler}가 ProblemDetail로 변환한다. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    private ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
