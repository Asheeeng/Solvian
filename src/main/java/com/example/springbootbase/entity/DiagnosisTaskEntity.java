package com.example.springbootbase.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 异步诊断任务实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("diagnosis_task")
public class DiagnosisTaskEntity {

    @TableId
    private String taskId;

    private String recordId;

    private String userId;

    private String username;

    private String role;

    private String status;

    private Integer progress;

    private String stageMessage;

    private String inputImageName;

    private String inputImagePath;

    private String inputImageHash;

    private String inputContentType;

    private Long inputFileSize;

    private String subjectScope;

    private Boolean isSocratic;

    private String visionModel;

    private String reasoningModel;

    private String visionResultJson;

    private String partialResultJson;

    private String finalResultJson;

    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime finishedAt;
}
