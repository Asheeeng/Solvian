package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 前端事件日志记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecord {
    private String eventId;
    private String userId;
    private String username;
    private String eventType;
    private String page;
    private String action;
    private Map<String, Object> payload;
    private String recordId;
    private Instant createdAt;
}
