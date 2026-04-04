package com.example.springbootbase.auth;

/**
 * 系统角色。
 */
public enum Role {
    STUDENT,
    TEACHER,
    ADMIN;

    public static Role fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("role 不能为空");
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "STUDENT" -> STUDENT;
            case "TEACHER" -> TEACHER;
            case "ADMIN" -> ADMIN;
            default -> throw new IllegalArgumentException("不支持的角色: " + raw);
        };
    }
}
