package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.request.LogEventRequest;
import com.example.springbootbase.dto.response.EventLogResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.EventLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 事件日志接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogService eventLogService;

    @PostMapping("/log-event")
    public EventLogResponse logEvent(@RequestBody LogEventRequest request, HttpServletRequest servletRequest) {
        SessionInfo sessionInfo = (SessionInfo) servletRequest.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return eventLogService.log(request, sessionInfo);
    }
}
