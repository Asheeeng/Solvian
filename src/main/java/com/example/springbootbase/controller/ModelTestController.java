package com.example.springbootbase.controller;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.ModelClientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模型连通性最小验证接口。
 */
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
public class ModelTestController {

    private final ModelClientService modelClientService;

    @GetMapping("/test")
    public Map<String, Object> test(HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录");
        }
        if (sessionInfo.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("当前接口仅老师可用");
        }
        return modelClientService.testTextModel();
    }
}

