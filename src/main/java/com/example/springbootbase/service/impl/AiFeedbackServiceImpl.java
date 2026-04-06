package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.request.AiFeedbackRequest;
import com.example.springbootbase.dto.response.AiFeedbackResponse;
import com.example.springbootbase.entity.AiFeedbackRecordEntity;
import com.example.springbootbase.entity.DiagnosisRecordEntity;
import com.example.springbootbase.mapper.AiFeedbackRecordMapper;
import com.example.springbootbase.mapper.DiagnosisRecordMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiFeedbackService;
import com.example.springbootbase.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * AI 反馈服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final DiagnosisRecordMapper diagnosisRecordMapper;
    private final AiFeedbackRecordMapper aiFeedbackRecordMapper;

    @Override
    public AiFeedbackResponse submit(AiFeedbackRequest request, SessionInfo sessionInfo) {
        if (request == null || request.getRecordId() == null || request.getRecordId().isBlank()) {
            throw new IllegalArgumentException("recordId 不能为空");
        }

        DiagnosisRecordEntity recordEntity = diagnosisRecordMapper.selectById(request.getRecordId());
        if (recordEntity == null) {
            return AiFeedbackResponse.builder()
                    .success(false)
                    .message("诊断记录不存在")
                    .build();
        }

        if (sessionInfo.getRole() == Role.STUDENT && !sessionInfo.getUserId().equals(recordEntity.getUserId())) {
            return AiFeedbackResponse.builder()
                    .success(false)
                    .message("无权操作该记录")
                    .build();
        }

        AiFeedbackType aiFeedbackType = AiFeedbackType.fromInput(request.getAiFeedback());
        ErrorType errorType = null;

        if (aiFeedbackType == AiFeedbackType.INACCURATE) {
            if (request.getErrorType() == null || request.getErrorType().isBlank()) {
                throw new IllegalArgumentException("识别不准确时必须提供 errorType");
            }
            errorType = ErrorType.fromInput(request.getErrorType());
        }

        recordEntity.setAiFeedback(aiFeedbackType.name());
        recordEntity.setErrorType(errorType == null ? null : errorType.name());
        recordEntity.setNote(request.getNote());
        diagnosisRecordMapper.updateById(recordEntity);

        String feedbackId = IdUtil.newId();
        aiFeedbackRecordMapper.insert(AiFeedbackRecordEntity.builder()
                .feedbackId(feedbackId)
                .recordId(recordEntity.getRecordId())
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole().name())
                .aiFeedback(aiFeedbackType.name())
                .errorType(errorType == null ? null : errorType.name())
                .note(request.getNote())
                .createdAt(OffsetDateTime.now())
                .build());

        return AiFeedbackResponse.builder()
                .success(true)
                .message("反馈保存成功")
                .feedbackId(feedbackId)
                .build();
    }
}
