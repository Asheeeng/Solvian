package com.example.springbootbase.auth;

/**
 * AI 识别反馈类型。
 */
public enum AiFeedbackType {
    ACCURATE,
    INACCURATE;

    public static AiFeedbackType fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("aiFeedback 不能为空");
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "ACCURATE", "识别准确" -> ACCURATE;
            case "INACCURATE", "识别不准确" -> INACCURATE;
            default -> throw new IllegalArgumentException("不支持的 aiFeedback: " + raw);
        };
    }
}
