package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.request.AiFeedbackRequest;
import com.example.springbootbase.dto.response.AiFeedbackResponse;
import com.example.springbootbase.model.AiFeedbackRecord;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiFeedbackService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 反馈服务实现。
 */
@Service
@RequiredArgsConstructor
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final InMemoryDataStore store;

    @Override
    public AiFeedbackResponse submit(AiFeedbackRequest request, SessionInfo sessionInfo) {
        if (request == null || request.getRecordId() == null || request.getRecordId().isBlank()) {
            throw new IllegalArgumentException("recordId 不能为空");
        }

        DiagnosisRecord record = store.getDiagnosisById().get(request.getRecordId());
        if (record == null) {
            return AiFeedbackResponse.builder()
                    .success(false)
                    .message("诊断记录不存在")
                    .build();
        }

        if (sessionInfo.getRole() == Role.STUDENT && !sessionInfo.getUserId().equals(record.getUserId())) {
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

        record.setAiFeedback(aiFeedbackType);
        record.setErrorType(errorType);
        record.setNote(request.getNote());

        String feedbackId = IdUtil.newId();
        store.getAiFeedbackRecords().add(AiFeedbackRecord.builder()
                .feedbackId(feedbackId)
                .recordId(record.getRecordId())
                .userId(sessionInfo.getUserId())
                .aiFeedback(aiFeedbackType)
                .errorType(errorType)
                .note(request.getNote())
                .createdAt(TimeUtil.now())
                .build());

        return AiFeedbackResponse.builder()
                .success(true)
                .message("反馈保存成功")
                .feedbackId(feedbackId)
                .build();
    }
}
