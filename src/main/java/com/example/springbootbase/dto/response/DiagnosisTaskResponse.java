package com.example.springbootbase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 异步诊断任务响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisTaskResponse {
    private String taskId;
    private String recordId;
    private String status;
    private Integer progress;
    private String stageMessage;
    private String errorMessage;
    private Map<String, Object> partialResult;
    private EvaluateResponse finalResult;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime finishedAt;
}
