package com.example.springbootbase.service;

import com.example.springbootbase.dto.response.SubmissionCreateResponse;
import com.example.springbootbase.dto.response.SubmissionListResponse;
import com.example.springbootbase.entity.SubmissionEntity;
import com.example.springbootbase.model.SessionInfo;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生作业提交服务。
 */
public interface SubmissionService {
    SubmissionCreateResponse createSubmission(MultipartFile file, SessionInfo sessionInfo);

    SubmissionListResponse listMine(SessionInfo sessionInfo, int limit);

    SubmissionListResponse listForTeacher(SessionInfo sessionInfo, Integer limit);

    SubmissionEntity requireSubmission(Long submissionId);

    Resource loadSubmissionImage(Long submissionId, SessionInfo sessionInfo);

    void ensureTeacherCanAccess(SubmissionEntity submission, SessionInfo sessionInfo);

    void ensureStudentCanAccess(SubmissionEntity submission, SessionInfo sessionInfo);

    String resolveImageContentType(SubmissionEntity submission);
}
