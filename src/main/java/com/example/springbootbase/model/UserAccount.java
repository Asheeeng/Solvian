package com.example.springbootbase.model;

import com.example.springbootbase.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 内存用户模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    private Long id;
    private String userId;
    private String username;
    private String password;
    private Role role;
    private Long classId;
    private String className;
    private Instant createdAt;
}
