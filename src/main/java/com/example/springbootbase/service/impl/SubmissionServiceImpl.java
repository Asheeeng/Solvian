package com.example.springbootbase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.SubmissionCreateResponse;
import com.example.springbootbase.dto.response.SubmissionListResponse;
import com.example.springbootbase.entity.SubmissionEntity;
import com.example.springbootbase.mapper.SubmissionMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.SubmissionService;
import com.example.springbootbase.service.SubmissionStorageService;
import com.example.springbootbase.vo.SubmissionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 学生作业提交服务实现。
 */
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionStorageService submissionStorageService;

    @Override
    public SubmissionCreateResponse createSubmission(MultipartFile file, SessionInfo sessionInfo) {
        ensureStudent(sessionInfo);
        if (sessionInfo.getClassId() == null) {
            throw new IllegalArgumentException("当前学生尚未分配班级");
        }

        String storedPath = submissionStorageService.store(file, file.getOriginalFilename());
        OffsetDateTime now = OffsetDateTime.now();
        SubmissionEntity entity = SubmissionEntity.builder()
                .studentId(sessionInfo.getId())
                .studentUserId(sessionInfo.getUserId())
                .studentName(sessionInfo.getUsername())
                .displayName(sessionInfo.getUsername())
                .classId(sessionInfo.getClassId())
                .fileName(resolveFileName(file))
                .imagePath(storedPath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .checkStatus("PENDING")
                .submitTime(now)
                .build();
        submissionMapper.insert(entity);

        return SubmissionCreateResponse.builder()
                .success(true)
                .message("提交成功")
                .submission(toSubmissionVO(entity))
                .build();
    }

    @Override
    public SubmissionListResponse listMine(SessionInfo sessionInfo, int limit) {
        ensureStudent(sessionInfo);
        int safeLimit = limit <= 0 ? 3 : Math.min(limit, 20);
        List<SubmissionEntity> list = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionEntity>()
                .eq(SubmissionEntity::getStudentId, sessionInfo.getId())
                .orderByDesc(SubmissionEntity::getSubmitTime)
                .last("LIMIT " + safeLimit));
        return SubmissionListResponse.builder()
                .total(list.size())
                .list(list.stream().map(this::toSubmissionVO).toList())
                .build();
    }

    @Override
    public SubmissionListResponse listForTeacher(SessionInfo sessionInfo, Integer limit) {
        ensureTeacher(sessionInfo);
        if (sessionInfo.getClassId() == null) {
            throw new IllegalArgumentException("当前老师尚未绑定班级");
        }
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 50);
        List<SubmissionEntity> list = submissionMapper.selectList(new LambdaQueryWrapper<SubmissionEntity>()
                .eq(SubmissionEntity::getClassId, sessionInfo.getClassId())
                .orderByDesc(SubmissionEntity::getSubmitTime)
                .last("LIMIT " + safeLimit));
        return SubmissionListResponse.builder()
                .total(list.size())
                .list(list.stream().map(this::toSubmissionVO).toList())
                .build();
    }

    @Override
    public SubmissionEntity requireSubmission(Long submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new IllegalArgumentException("提交记录不存在");
        }
        return submission;
    }

    @Override
    public Resource loadSubmissionImage(Long submissionId, SessionInfo sessionInfo) {
        SubmissionEntity submission = requireSubmission(submissionId);
        if (sessionInfo.getRole() == Role.TEACHER) {
            ensureTeacherCanAccess(submission, sessionInfo);
        } else if (sessionInfo.getRole() == Role.STUDENT) {
            ensureStudentCanAccess(submission, sessionInfo);
        }
        return submissionStorageService.loadAsResource(submission);
    }

    @Override
    public void ensureTeacherCanAccess(SubmissionEntity submission, SessionInfo sessionInfo) {
        ensureTeacher(sessionInfo);
        if (sessionInfo.getClassId() == null || !sessionInfo.getClassId().equals(submission.getClassId())) {
            throw new IllegalArgumentException("无权查看该班级的提交记录");
        }
    }

    @Override
    public void ensureStudentCanAccess(SubmissionEntity submission, SessionInfo sessionInfo) {
        ensureStudent(sessionInfo);
        if (!submission.getStudentId().equals(sessionInfo.getId())) {
            throw new IllegalArgumentException("无权查看他人的提交记录");
        }
    }

    @Override
    public String resolveImageContentType(SubmissionEntity submission) {
        String contentType = submission.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private void ensureStudent(SessionInfo sessionInfo) {
        if (sessionInfo == null || sessionInfo.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("当前操作仅学生可用");
        }
    }

    private void ensureTeacher(SessionInfo sessionInfo) {
        if (sessionInfo == null || sessionInfo.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("当前操作仅老师可用");
        }
    }

    private String resolveFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return (fileName == null || fileName.isBlank()) ? "未命名作业" : fileName;
    }

    private SubmissionItemVO toSubmissionVO(SubmissionEntity entity) {
        return SubmissionItemVO.builder()
                .id(entity.getId())
                .studentId(entity.getStudentId())
                .studentUserId(entity.getStudentUserId())
                .studentName(entity.getStudentName())
                .classId(entity.getClassId())
                .fileName(entity.getFileName())
                .checkStatus(entity.getCheckStatus())
                .checkResultJson(entity.getCheckResultJson())
                .diagnosisRecordId(entity.getDiagnosisRecordId())
                .submitTime(toInstant(entity.getSubmitTime()))
                .checkedAt(toInstant(entity.getCheckedAt()))
                .build();
    }

    private Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
