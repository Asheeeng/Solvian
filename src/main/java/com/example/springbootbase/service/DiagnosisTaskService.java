package com.example.springbootbase.service;

import com.example.springbootbase.dto.response.DiagnosisTaskResponse;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.model.SessionInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 异步诊断任务服务。
 */
public interface DiagnosisTaskService {
    DiagnosisTaskResponse createTask(MultipartFile file, boolean isSocratic, String problemType, SessionInfo sessionInfo);

    DiagnosisTaskResponse getTask(String taskId, SessionInfo sessionInfo);

    DiagnosisTaskEntity requireTask(String taskId);

    void updateTask(DiagnosisTaskEntity entity);

    void markFailed(String taskId, String errorMessage);
}
