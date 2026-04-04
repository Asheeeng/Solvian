package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.response.HistoryResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.HistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 历史记录接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/history")
    public HistoryResponse history(HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return historyService.listHistory(sessionInfo);
    }
}
