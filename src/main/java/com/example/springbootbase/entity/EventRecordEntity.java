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
 * 前端事件日志实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("event_record")
public class EventRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String userId;

    private String username;

    private String role;

    private String eventName;

    private String eventType;

    private String page;

    private String action;

    private String recordId;

    private String payloadJson;

    private OffsetDateTime createdAt;
}

