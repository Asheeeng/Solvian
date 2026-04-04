package com.example.springbootbase.util;

import java.time.Instant;

/**
 * 时间工具。
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static Instant now() {
        return Instant.now();
    }
}
