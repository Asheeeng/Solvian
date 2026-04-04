package com.example.springbootbase.service;

import com.example.springbootbase.dto.request.LoginRequest;
import com.example.springbootbase.dto.request.RegisterRequest;
import com.example.springbootbase.dto.response.LoginResponse;
import com.example.springbootbase.dto.response.RegisterResponse;
import com.example.springbootbase.model.SessionInfo;

/**
 * 认证服务。
 */
public interface AuthService {
    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    SessionInfo getSessionByToken(String token);

    void logout(String token);
}
