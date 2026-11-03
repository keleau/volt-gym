package com.gym.gymmanagementsystem.common;

import lombok.Getter;

import java.util.Map;

/**
 * 业务错误：直接映射为契约里的错误体 {code, message, fields}。
 */
@Getter
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public static ApiException validation(String message, Map<String, String> fields) {
        return new ApiException(400, "VALIDATION_ERROR", message, fields);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }
}
