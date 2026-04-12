package com.example.springbootbase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.dto.response.DiagnosisTaskResponse;
import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.entity.SubmissionEntity;
import com.example.springbootbase.mapper.DiagnosisTaskMapper;
import com.example.springbootbase.mapper.SubmissionMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.DiagnosisInputStoreService;
import com.example.springbootbase.service.DiagnosisQueueService;
import com.example.springbootbase.service.DiagnosisTaskService;
import com.example.springbootbase.service.ImagePreprocessService;
import com.example.springbootbase.service.SubmissionStorageService;
import com.example.springbootbase.util.IdUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步诊断任务服务实现。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisTaskServiceImpl implements DiagnosisTaskService {

    private final ImagePreprocessService imagePreprocessService;
    private final DiagnosisInputStoreService diagnosisInputStoreService;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final SubmissionMapper submissionMapper;
    private final DiagnosisQueueService diagnosisQueueService;
    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper;
    private final SubmissionStorageService submissionStorageService;

    @Override
    public DiagnosisTaskResponse createTask(MultipartFile file, boolean isSocratic, String problemType, SessionInfo sessionInfo) {
        validateTeacherScope(sessionInfo);
        String subjectScope = normalizeSubjectScope(problemType);
        com.example.springbootbase.model.PreprocessedImage preprocessedImage = imagePreprocessService.preprocess(file);
        DiagnosisTaskEntity entity = buildTaskEntity(
                preprocessedImage,
                isSocratic,
                subjectScope,
                sessionInfo,
                null,
                sessionInfo.getUserId(),
                sessionInfo.getUsername(),
                sessionInfo.getRole()
        );
        diagnosisTaskMapper.insert(entity);
        diagnosisQueueService.enqueue(entity.getTaskId());
        return toResponse(entity);
    }

    @Override
    public DiagnosisTaskResponse createTaskForSubmissionId(Long submissionId,
                                                           boolean isSocratic,
                                                           String problemType,
                                                           SessionInfo sessionInfo) {
        validateTeacherScope(sessionInfo);
        SubmissionEntity submission = requireSubmission(submissionId);
        if (sessionInfo.getClassId() == null || !sessionInfo.getClassId().equals(submission.getClassId())) {
            throw new IllegalArgumentException("无权检测该班级的作业");
        }

        byte[] bytes = submissionStorageService.readBytes(submission);
        com.example.springbootbase.model.PreprocessedImage preprocessedImage = imagePreprocessService.preprocess(
                submission.getFileName(),
                submission.getContentType(),
                bytes
        );
        DiagnosisTaskEntity entity = buildTaskEntity(
                preprocessedImage,
                isSocratic,
                normalizeSubjectScope(problemType),
                sessionInfo,
                submission,
                submission.getStudentUserId(),
                submission.getStudentName(),
                Role.STUDENT
        );

        submission.setCheckStatus("CHECKING");
        submission.setCheckedAt(null);
        submissionMapper.updateById(submission);

        diagnosisTaskMapper.insert(entity);
        diagnosisQueueService.enqueue(entity.getTaskId());
        return toResponse(entity);
    }

    @Override
    public DiagnosisTaskResponse getTask(String taskId, SessionInfo sessionInfo) {
        DiagnosisTaskEntity task = requireTask(taskId);
        validateAccess(task, sessionInfo);
        return toResponse(task);
    }

    @Override
    public DiagnosisTaskEntity requireTask(String taskId) {
        DiagnosisTaskEntity task = diagnosisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("诊断任务不存在");
        }
        return task;
    }

    @Override
    public void updateTask(DiagnosisTaskEntity entity) {
        diagnosisTaskMapper.updateById(entity);
    }

    @Override
    public void markFailed(String taskId, String errorMessage) {
        DiagnosisTaskEntity task = requireTask(taskId);
        task.setStatus("failed");
        task.setProgress(100);
        task.setStageMessage("诊断失败，请稍后重试");
        task.setErrorMessage(errorMessage == null || errorMessage.isBlank() ? "未知错误" : errorMessage);
        task.setUpdatedAt(OffsetDateTime.now());
        task.setFinishedAt(OffsetDateTime.now());
        diagnosisTaskMapper.updateById(task);
    }

    private DiagnosisTaskResponse toResponse(DiagnosisTaskEntity entity) {
        return DiagnosisTaskResponse.builder()
                .taskId(entity.getTaskId())
                .recordId(entity.getRecordId())
                .status(entity.getStatus())
                .progress(entity.getProgress())
                .stageMessage(entity.getStageMessage())
                .errorMessage(entity.getErrorMessage())
                .partialResult(readPartialResult(entity.getPartialResultJson()))
                .finalResult(readFinalResult(entity.getFinalResultJson()))
                .createdAt(entity.getCreatedAt())
                .startedAt(entity.getStartedAt())
                .updatedAt(entity.getUpdatedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }

    private Map<String, Object> readPartialResult(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private EvaluateResponse readFinalResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EvaluateResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void validateTeacherScope(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录，无法发起检测");
        }
        if (sessionInfo.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("当前阶段仅支持老师端检测");
        }
    }

    private void validateAccess(DiagnosisTaskEntity task, SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录，无法查看诊断任务");
        }
        if (sessionInfo.getRole() == Role.ADMIN) {
            return;
        }
        if (!task.getUserId().equals(sessionInfo.getUserId())) {
            throw new IllegalArgumentException("无权查看该诊断任务");
        }
    }

    private String normalizeSubjectScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return "matrix";
        }
        return "matrix";
    }

    private SubmissionEntity requireSubmission(Long submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        return submission;
    }

    private DiagnosisTaskEntity buildTaskEntity(com.example.springbootbase.model.PreprocessedImage preprocessedImage,
                                                boolean isSocratic,
                                                String subjectScope,
                                                SessionInfo sessionInfo,
                                                SubmissionEntity submission,
                                                String targetUserId,
                                                String targetUsername,
                                                Role targetRole) {
        String taskId = IdUtil.newId();
        String inputPath = diagnosisInputStoreService.store(taskId, preprocessedImage);
        OffsetDateTime now = OffsetDateTime.now();
        return DiagnosisTaskEntity.builder()
                .taskId(taskId)
                .recordId(null)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole().name())
                .classId(submission == null ? sessionInfo.getClassId() : submission.getClassId())
                .submissionId(submission == null ? null : submission.getId())
                .targetUserId(targetUserId)
                .targetUsername(targetUsername)
                .targetRole(targetRole == null ? null : targetRole.name())
                .status("queued")
                .progress(6)
                .stageMessage("任务已创建，等待进入视觉识别队列")
                .inputImageName(preprocessedImage.getFileName())
                .inputImagePath(inputPath)
                .inputImageHash(preprocessedImage.getImageHash())
                .inputContentType(preprocessedImage.getContentType())
                .inputFileSize(preprocessedImage.getOriginalFileSize())
                .subjectScope(subjectScope)
                .isSocratic(isSocratic)
                .visionModel(aiModelProperties.getVisionModel())
                .reasoningModel(aiModelProperties.getModel())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
