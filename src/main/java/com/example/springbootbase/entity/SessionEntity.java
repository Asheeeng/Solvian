package com.example.springbootbase.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 登录会话实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("app_session")
public class SessionEntity {

    @TableId
    private String token;

    private String userId;

    private String username;

    private String role;

    private OffsetDateTime createdAt;
}

