package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.ReportService;
import com.example.springbootbase.store.InMemoryDataStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 报告服务实现。
 * 当前返回占位内容，后续可替换为真实 PDF 生成。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InMemoryDataStore store;

    @Override
    public byte[] downloadPdf(String recordId, SessionInfo sessionInfo) {
        DiagnosisRecord record = store.getDiagnosisById().get(recordId);
        if (record == null) {
            throw new IllegalArgumentException("记录不存在");
        }

        if (sessionInfo.getRole() == Role.STUDENT && !sessionInfo.getUserId().equals(record.getUserId())) {
            throw new IllegalArgumentException("无权下载该报告");
        }

        String content = "PDF 报告占位\n"
                + "recordId=" + record.getRecordId() + "\n"
                + "status=" + record.getStatus() + "\n"
                + "feedback=" + record.getFeedback() + "\n";

        return content.getBytes(StandardCharsets.UTF_8);
    }
}
