package com.example.springbootbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 用户表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("app_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;

    private String username;

    private String password;

    private String role;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
