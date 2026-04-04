package com.example.springbootbase.util;

import java.util.UUID;

/**
 * ID 工具。
 */
public final class IdUtil {

    private IdUtil() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
