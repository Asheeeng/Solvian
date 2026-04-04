package com.example.springbootbase.vo;

import com.example.springbootbase.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户输出结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private String userId;
    private String username;
    private Role role;
}
