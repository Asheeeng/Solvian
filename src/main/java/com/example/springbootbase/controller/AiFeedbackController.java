package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.request.AiFeedbackRequest;
import com.example.springbootbase.dto.response.AiFeedbackResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 识别反馈接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    @PostMapping("/ai-feedback")
    public AiFeedbackResponse submit(@RequestBody AiFeedbackRequest request, HttpServletRequest servletRequest) {
        SessionInfo sessionInfo = (SessionInfo) servletRequest.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        return aiFeedbackService.submit(request, sessionInfo);
    }
}
