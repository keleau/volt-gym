package com.gym.gymmanagementsystem.dto;

import lombok.Data;

import java.util.Map;

/**
 * 统一错误响应体，与 GlobalExceptionHandler 及 docs/shared-backend-api-final.md 保持一致。
 */
@Data
public class ApiErrorResponse {
    private String code;
    private String message;
    private Map<String, String> fields;
    private String requestId;

    public ApiErrorResponse(String code, String message, Map<String, String> fields, String requestId) {
        this.code = code;
        this.message = message;
        this.fields = fields;
        this.requestId = requestId;
    }
}
