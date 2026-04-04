package com.example.springbootbase.service;

import com.example.springbootbase.model.DiagnosisResult;

/**
 * 模型输出解析服务。
 */
public interface DiagnosisResultParser {
    DiagnosisResult parse(String rawModelOutput);
}
