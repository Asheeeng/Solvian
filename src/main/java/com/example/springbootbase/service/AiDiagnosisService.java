package com.example.springbootbase.service;

import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.model.SessionInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 诊断编排服务。
 */
public interface AiDiagnosisService {
    EvaluateResponse evaluate(MultipartFile file, boolean isSocratic, SessionInfo sessionInfo);
}
