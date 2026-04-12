package com.example.springbootbase.worker;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.model.VisionStageResult;
import com.example.springbootbase.service.ModelClientService;
import com.example.springbootbase.service.impl.DiagnosisResultComposer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 推理分析 worker。
 */
@Component
@RequiredArgsConstructor
public class ReasoningWorker {

    private final ModelClientService modelClientService;
    private final DiagnosisResultComposer diagnosisResultComposer;

    public EvaluateResponse process(DiagnosisTaskEntity task,
                                    com.example.springbootbase.model.PreprocessedImage image,
                                    VisionStageResult visionStageResult) {
        ModelChainResult chainResult = modelClientService.analyzeReasoning(
                visionStageResult,
                Boolean.TRUE.equals(task.getIsSocratic()),
                task.getSubjectScope()
        );
        DiagnosisResult finalResult = diagnosisResultComposer.compose(chainResult, task.getSubjectScope());
        return diagnosisResultComposer.persistAndBuildResponse(
                toSessionInfo(task),
                image,
                Boolean.TRUE.equals(task.getIsSocratic()),
                finalResult,
                task.getSubjectScope(),
                task.getSubmissionId()
        );
    }

    private SessionInfo toSessionInfo(DiagnosisTaskEntity task) {
        return SessionInfo.builder()
                .userId(task.getTargetUserId() == null || task.getTargetUserId().isBlank() ? task.getUserId() : task.getTargetUserId())
                .username(task.getTargetUsername() == null || task.getTargetUsername().isBlank() ? task.getUsername() : task.getTargetUsername())
                .role(Role.valueOf(task.getTargetRole() == null || task.getTargetRole().isBlank() ? task.getRole() : task.getTargetRole()))
                .classId(task.getClassId())
                .createdAt(Instant.now())
                .build();
    }
}
