package com.example.springbootbase.service;

import com.example.springbootbase.model.DiagnosisResult;

/**
 * 错误定位与结果兜底服务。
 */
public interface ErrorAnalysisService {
    DiagnosisResult analyze(DiagnosisResult diagnosisResult, String rawModelOutput);
}
