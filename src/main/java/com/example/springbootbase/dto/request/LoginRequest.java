package com.example.springbootbase.dto.request;

import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
    private String role;
}
