package com.example.springbootbase.service;

import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionStageResult;

import java.util.Map;

/**
 * 模型调用服务。
 */
public interface ModelClientService {
    VisionStageResult analyzeVision(PreprocessedImage preprocessedImage, String subjectScope);

    ModelChainResult analyzeReasoning(VisionStageResult visionStageResult, boolean isSocratic, String subjectScope);

    ModelChainResult analyze(PreprocessedImage preprocessedImage, boolean isSocratic, String subjectScope);

    Map<String, Object> testTextModel();

    Map<String, Object> testTextModel(String modelOverride, String promptOverride);
}
