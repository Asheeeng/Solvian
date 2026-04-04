package com.example.springbootbase.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * 事件日志请求。
 */
@Data
public class LogEventRequest {
    private String eventType;
    private String page;
    private String action;
    private Map<String, Object> payload;
    private String recordId;
    private Long ts;
}
