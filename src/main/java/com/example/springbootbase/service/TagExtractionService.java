package com.example.springbootbase.service;

import com.example.springbootbase.model.DiagnosisResult;

import java.util.List;

/**
 * 标签提取与规范化服务。
 */
public interface TagExtractionService {
    List<String> extractTags(DiagnosisResult diagnosisResult, String rawModelOutput);
}
