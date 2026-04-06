package com.example.springbootbase.service;

import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;

/**
 * 提示词构建服务。
 */
public interface PromptBuilderService {
    String buildVisionPrompt(PreprocessedImage preprocessedImage, String subjectScope);

    String buildReasoningPrompt(VisionExtractionResult visionExtractionResult, boolean isSocratic, String subjectScope);
}
