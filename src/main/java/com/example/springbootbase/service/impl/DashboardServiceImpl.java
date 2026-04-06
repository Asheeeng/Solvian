package com.example.springbootbase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.DashboardSummaryResponse;
import com.example.springbootbase.entity.DiagnosisRecordEntity;
import com.example.springbootbase.mapper.DiagnosisRecordMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.DashboardService;
import com.example.springbootbase.vo.DashboardSummaryVO;
import com.example.springbootbase.vo.HistoryItemVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DiagnosisRecordMapper diagnosisRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardSummaryResponse getSummary(SessionInfo sessionInfo) {
        List<DiagnosisRecordEntity> visibleRecords = collectVisibleRecords(sessionInfo);

        Map<String, Long> errorTypeCount = new LinkedHashMap<>();
        Map<String, Long> aiFeedbackCount = new LinkedHashMap<>();

        for (DiagnosisRecordEntity record : visibleRecords) {
            ErrorType errorType = parseErrorType(record.getErrorType());
            AiFeedbackType aiFeedbackType = parseAiFeedback(record.getAiFeedback());

            if (errorType != null) {
                String key = errorType.getLabel();
                errorTypeCount.put(key, errorTypeCount.getOrDefault(key, 0L) + 1);
            }
            if (aiFeedbackType != null) {
                String key = aiFeedbackType.name();
                aiFeedbackCount.put(key, aiFeedbackCount.getOrDefault(key, 0L) + 1);
            }
        }

        List<HistoryItemVO> recent = visibleRecords.stream()
                .limit(5)
                .map(record -> HistoryItemVO.builder()
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
                        .build())
                .toList();

        DashboardSummaryVO summaryVO = DashboardSummaryVO.builder()
                .totalEvaluations(visibleRecords.size())
                .errorTypeCount(errorTypeCount)
                .aiFeedbackCount(aiFeedbackCount)
                .recentRecords(recent)
                .build();

        return DashboardSummaryResponse.builder()
                .summary(summaryVO)
                .build();
    }

    private List<DiagnosisRecordEntity> collectVisibleRecords(SessionInfo sessionInfo) {
        LambdaQueryWrapper<DiagnosisRecordEntity> wrapper = new LambdaQueryWrapper<DiagnosisRecordEntity>()
                .orderByDesc(DiagnosisRecordEntity::getCreatedAt);
        if (sessionInfo.getRole() == Role.STUDENT) {
            wrapper.eq(DiagnosisRecordEntity::getUserId, sessionInfo.getUserId());
        }
        return new ArrayList<>(diagnosisRecordMapper.selectList(wrapper));
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
