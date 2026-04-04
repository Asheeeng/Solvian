package com.example.springbootbase.auth;

/**
 * 错误类型。
 */
public enum ErrorType {
    SCALAR_ERROR("标量误差"),
    COPY_ERROR("复制错误"),
    OTHER("其他");

    private final String label;

    ErrorType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ErrorType fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("errorType 不能为空");
        }

        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "SCALAR_ERROR", "标量误差" -> SCALAR_ERROR;
            case "COPY_ERROR", "复制错误" -> COPY_ERROR;
            case "OTHER", "其他" -> OTHER;
            default -> throw new IllegalArgumentException("不支持的 errorType: " + raw);
        };
    }
}
