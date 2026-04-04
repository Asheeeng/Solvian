package com.example.springbootbase.service;

import com.example.springbootbase.model.DiagnosisResult;

/**
 * 数学校验服务。
 */
public interface MathValidationService {
    DiagnosisResult validate(DiagnosisResult diagnosisResult);
}
