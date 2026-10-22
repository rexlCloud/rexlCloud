package de.rexlcloud.restmodule.web;

import lombok.Getter;

/**
 * Ends a request with an error response of the form {@code {"error": "...", "message": "..."}}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "bad_request", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "unauthorized", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "forbidden", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "not_found", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "conflict", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, "too_many_requests", message);
    }

}
