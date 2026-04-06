package com.example.springbootbase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.HistoryResponse;
import com.example.springbootbase.entity.DiagnosisRecordEntity;
import com.example.springbootbase.mapper.DiagnosisRecordMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.HistoryService;
import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.vo.HistoryItemVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final DiagnosisRecordMapper diagnosisRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public HistoryResponse listHistory(SessionInfo sessionInfo) {
        List<HistoryItemVO> visible = new ArrayList<>();
        LambdaQueryWrapper<DiagnosisRecordEntity> wrapper = new LambdaQueryWrapper<DiagnosisRecordEntity>()
                .orderByDesc(DiagnosisRecordEntity::getCreatedAt);
        if (sessionInfo.getRole() == Role.STUDENT) {
            wrapper.eq(DiagnosisRecordEntity::getUserId, sessionInfo.getUserId());
        }
        List<DiagnosisRecordEntity> rows = diagnosisRecordMapper.selectList(wrapper);
        for (DiagnosisRecordEntity row : rows) {
            visible.add(toHistoryVO(row));
        }

        return HistoryResponse.builder()
                .total(visible.size())
                .list(visible)
                .build();
    }

    private HistoryItemVO toHistoryVO(DiagnosisRecordEntity record) {
        return HistoryItemVO.builder()
                .recordId(record.getRecordId())
                .username(record.getUsername())
                .role(Role.fromInput(record.getRole()))
                .fileName(record.getImageName())
                .status(record.getStatus())
                .errorIndex(record.getErrorIndex())
                .steps(parseStringList(record.getStepsJson()))
                .tags(parseStringList(record.getTagsJson()))
                .feedback(record.getFeedback())
                .isSocratic(record.getIsSocratic())
                .aiFeedback(parseAiFeedback(record.getAiFeedback()))
                .errorType(parseErrorType(record.getErrorType()))
                .note(record.getNote())
                .createdAt(toInstant(record.getCreatedAt()))
                .build();
    }

    private List<String> parseStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private AiFeedbackType parseAiFeedback(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return AiFeedbackType.fromInput(raw);
    }

    private ErrorType parseErrorType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ErrorType.fromInput(raw);
    }

    private Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
