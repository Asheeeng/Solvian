package com.example.springbootbase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事件日志响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLogResponse {
    private boolean success;
    private String message;
    private String eventId;
}
