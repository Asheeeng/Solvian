package com.example.springbootbase.model;

import com.example.springbootbase.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 内存会话模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String token;
    private String userId;
    private String username;
    private Role role;
    private Instant createdAt;
}
