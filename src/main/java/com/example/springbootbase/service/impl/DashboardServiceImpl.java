package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.DashboardSummaryResponse;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.DashboardService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.vo.DashboardSummaryVO;
import com.example.springbootbase.vo.HistoryItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final InMemoryDataStore store;

    @Override
    public DashboardSummaryResponse getSummary(SessionInfo sessionInfo) {
        List<DiagnosisRecord> visibleRecords = collectVisibleRecords(sessionInfo);

        Map<String, Long> errorTypeCount = new LinkedHashMap<>();
        Map<String, Long> aiFeedbackCount = new LinkedHashMap<>();

        for (DiagnosisRecord record : visibleRecords) {
            if (record.getErrorType() != null) {
                String key = record.getErrorType().getLabel();
                errorTypeCount.put(key, errorTypeCount.getOrDefault(key, 0L) + 1);
            }
            if (record.getAiFeedback() != null) {
                String key = record.getAiFeedback().name();
                aiFeedbackCount.put(key, aiFeedbackCount.getOrDefault(key, 0L) + 1);
            }
        }

        List<HistoryItemVO> recent = visibleRecords.stream()
                .limit(5)
                .map(record -> HistoryItemVO.builder()
                        .recordId(record.getRecordId())
                        .username(record.getUsername())
                        .role(record.getRole())
                        .fileName(record.getFileName())
                        .status(record.getStatus())
                        .errorIndex(record.getErrorIndex())
                        .steps(record.getSteps())
                        .tags(record.getTags())
                        .feedback(record.getFeedback())
                        .isSocratic(record.getIsSocratic())
                        .aiFeedback(record.getAiFeedback())
                        .errorType(record.getErrorType())
                        .note(record.getNote())
                        .createdAt(record.getCreatedAt())
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

    private List<DiagnosisRecord> collectVisibleRecords(SessionInfo sessionInfo) {
        List<DiagnosisRecord> visible = new ArrayList<>();
        for (String recordId : store.getDiagnosisOrder()) {
            DiagnosisRecord record = store.getDiagnosisById().get(recordId);
            if (record == null) {
                continue;
            }
            if (sessionInfo.getRole() == Role.STUDENT && !sessionInfo.getUserId().equals(record.getUserId())) {
                continue;
            }
            visible.add(record);
        }
        return visible;
    }
}
