package com.example.springbootbase.service;

import com.example.springbootbase.model.DiagnosisResult;

/**
 * 模型响应解析服务。
 */
public interface ResponseParserService {
    DiagnosisResult parse(String rawModelOutput);
}
