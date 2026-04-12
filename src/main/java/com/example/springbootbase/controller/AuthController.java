package com.example.springbootbase.controller;

import com.example.springbootbase.config.AuthInterceptor;
import com.example.springbootbase.dto.request.LoginRequest;
import com.example.springbootbase.dto.request.RegisterRequest;
import com.example.springbootbase.dto.response.LoginResponse;
import com.example.springbootbase.dto.response.RegisterResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AuthService;
import com.example.springbootbase.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        if (sessionInfo != null) {
            authService.logout(sessionInfo.getToken());
        }
        return Map.of("success", true, "message", "已退出登录");
    }

    @GetMapping("/me")
    public UserVO me(HttpServletRequest request) {
        SessionInfo sessionInfo = (SessionInfo) request.getAttribute(AuthInterceptor.CURRENT_SESSION_KEY);
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录");
        }

        return UserVO.builder()
                .id(sessionInfo.getId())
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole())
                .classId(sessionInfo.getClassId())
                .className(sessionInfo.getClassName())
                .build();
    }
}
