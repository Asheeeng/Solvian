package com.example.springbootbase.dto.request;

import lombok.Data;

/**
 * 注册请求。
 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String role;
}
