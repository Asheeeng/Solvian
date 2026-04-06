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
 * AI 反馈记录实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_feedback_record")
public class AiFeedbackRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String feedbackId;

    private String recordId;

    private String userId;

    private String username;

    private String role;

    private String aiFeedback;

    private String errorType;

    private String note;

    private OffsetDateTime createdAt;
}

