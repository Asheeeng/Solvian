package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.service.MathValidationService;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * 数学校验实现。
 * 当前阶段为轻量占位，后续可接入更复杂的矩阵/代数校验。
 */
@Service
public class MathValidationServiceImpl implements MathValidationService {

    @Override
    public DiagnosisResult validate(DiagnosisResult diagnosisResult) {
        if (diagnosisResult.getMathData() == null) {
            diagnosisResult.setMathData(new HashMap<>());
        }

        if ("correct".equals(diagnosisResult.getStatus())) {
            diagnosisResult.setErrorIndex(null);
        } else if (diagnosisResult.getErrorIndex() == null) {
            diagnosisResult.setErrorIndex(1);
        }

        if (diagnosisResult.getFeedback() == null || diagnosisResult.getFeedback().isBlank()) {
            diagnosisResult.setFeedback("已完成诊断，但反馈内容为空，请稍后重试。");
        }

        return diagnosisResult;
    }
}
