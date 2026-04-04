package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.HistoryResponse;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.HistoryService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.vo.HistoryItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录服务实现。
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final InMemoryDataStore store;

    @Override
    public HistoryResponse listHistory(SessionInfo sessionInfo) {
        List<HistoryItemVO> visible = new ArrayList<>();

        for (String recordId : store.getDiagnosisOrder()) {
            DiagnosisRecord record = store.getDiagnosisById().get(recordId);
            if (record == null) {
                continue;
            }

            if (sessionInfo.getRole() == Role.STUDENT && !sessionInfo.getUserId().equals(record.getUserId())) {
                continue;
            }

            visible.add(toHistoryVO(record));
        }

        return HistoryResponse.builder()
                .total(visible.size())
                .list(visible)
                .build();
    }

    private HistoryItemVO toHistoryVO(DiagnosisRecord record) {
        return HistoryItemVO.builder()
                .recordId(record.getRecordId())
                .username(record.getUsername())
                .role(record.getRole())
                .fileName(record.getFileName())
                .status(record.getStatus())
                .errorIndex(record.getErrorIndex())
                .steps(record.getSteps())
                .feedback(record.getFeedback())
                .isSocratic(record.getIsSocratic())
                .aiFeedback(record.getAiFeedback())
                .errorType(record.getErrorType())
                .note(record.getNote())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
