package com.example.springbootbase.service;

import com.example.springbootbase.dto.request.LogEventRequest;
import com.example.springbootbase.dto.response.EventLogResponse;
import com.example.springbootbase.model.SessionInfo;

/**
 * 事件日志服务。
 */
public interface EventLogService {
    EventLogResponse log(LogEventRequest request, SessionInfo sessionInfo);
}
