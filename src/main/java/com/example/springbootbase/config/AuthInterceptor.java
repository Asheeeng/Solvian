package com.example.springbootbase.config;

import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 简单认证拦截器。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CURRENT_SESSION_KEY = "CURRENT_SESSION";

    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api")) {
            return true;
        }

        // 放行预检请求，避免跨域场景被鉴权拦截。
        if (RequestMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (path.equals("/api/auth/register") || path.equals("/api/auth/login")) {
            return true;
        }

        String token = request.getHeader("X-Auth-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }

        SessionInfo sessionInfo = authService.getSessionByToken(token);
        if (sessionInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"success\":false,\"message\":\"未登录或会话失效\"}");
            return false;
        }

        request.setAttribute(CURRENT_SESSION_KEY, sessionInfo);
        return true;
    }
}
