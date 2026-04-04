package com.example.springbootbase.service;

import com.example.springbootbase.model.SessionInfo;

/**
 * 报告服务。
 */
public interface ReportService {
    byte[] downloadPdf(String recordId, SessionInfo sessionInfo);
}
