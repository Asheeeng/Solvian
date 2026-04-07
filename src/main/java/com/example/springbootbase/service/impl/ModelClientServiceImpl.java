package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.model.VisionStageResult;
import com.example.springbootbase.service.ModelClientService;
import com.example.springbootbase.service.PromptBuilderService;
import com.example.springbootbase.service.VisionExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 模型调用编排实现（视觉识别 + 文本推理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelClientServiceImpl implements ModelClientService {

    private final AiModelProperties aiModelProperties;
    private final GlmModelClientService glmModelClientService;
    private final MockModelClientService mockModelClientService;
    private final PromptBuilderService promptBuilderService;
    private final VisionExtractionService visionExtractionService;

    @Override
    public VisionStageResult analyzeVision(PreprocessedImage preprocessedImage, String subjectScope) {
        validateProvider();

        if (aiModelProperties.isMockEnabled()) {
            log.warn("[vision-stage] mockEnabled=true，当前走 mock 视觉结果（仅用于本地调试）");
            VisionExtractionResult fallbackVision = VisionExtractionResult.builder()
                    .problemText("mock mode")
                    .studentSteps(java.util.List.of("识别到矩阵题题干", "识别到中间演算步骤"))
                    .matrixExpressions(java.util.List.of("A+B", "c_{12}=a_{12}-b_{12}"))
                    .imageHighlights(java.util.List.of())
                    .isMatrixProblem(true)
                    .confidence(1.0d)
                    .rawSummary("mock vision result")
                    .build();
            return VisionStageResult.builder()
                    .visionModel("mock-vision")
                    .visionRaw("{\"problemText\":\"mock mode\"}")
                    .visionExtractionResult(fallbackVision)
                    .cacheHit(false)
                    .build();
        }

        log.info(
                "[model-chain] provider={}, visionModel={}, reasoningModel={}, baseUrlHost={}, apiKeyMasked={}",
                aiModelProperties.getProvider(),
                aiModelProperties.getVisionModel(),
                aiModelProperties.getModel(),
                extractHost(aiModelProperties.getBaseUrl()),
                aiModelProperties.maskedApiKey()
        );

        String visionPrompt = promptBuilderService.buildVisionPrompt(preprocessedImage, subjectScope);
        String visionRaw;
        try {
            visionRaw = glmModelClientService.callVisionModel(
                    aiModelProperties.getVisionModel(),
                    preprocessedImage,
                    visionPrompt
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("视觉模型调用失败: " + ex.getMessage());
        }

        return VisionStageResult.builder()
                .visionModel(aiModelProperties.getVisionModel())
                .visionRaw(visionRaw)
                .visionExtractionResult(visionExtractionService.parse(visionRaw))
                .cacheHit(false)
                .build();
    }

    @Override
    public ModelChainResult analyzeReasoning(VisionStageResult visionStageResult, boolean isSocratic, String subjectScope) {
        validateProvider();

        if (visionStageResult == null || visionStageResult.getVisionExtractionResult() == null) {
            throw new IllegalArgumentException("视觉阶段结果为空，无法继续推理");
        }

        if (aiModelProperties.isMockEnabled()) {
            String raw = mockModelClientService.analyze(
                    PreprocessedImage.builder().fileSize(1).build(),
                    isSocratic,
                    subjectScope,
                    "mock-enabled=true"
            );
            return ModelChainResult.builder()
                    .visionModel(visionStageResult.getVisionModel())
                    .reasoningModel("mock-reasoning")
                    .visionRaw(visionStageResult.getVisionRaw())
                    .reasoningRaw(raw)
                    .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                    .build();
        }

        String reasoningPrompt = promptBuilderService.buildReasoningPrompt(
                visionStageResult.getVisionExtractionResult(),
                isSocratic,
                subjectScope
        );

        String reasoningRaw;
        try {
            reasoningRaw = glmModelClientService.callTextModel(
                    aiModelProperties.getModel(),
                    reasoningPrompt
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("推理模型调用失败: " + ex.getMessage());
        }

        return ModelChainResult.builder()
                .visionModel(visionStageResult.getVisionModel())
                .reasoningModel(aiModelProperties.getModel())
                .visionRaw(visionStageResult.getVisionRaw())
                .reasoningRaw(reasoningRaw)
                .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                .build();
    }

    @Override
    public ModelChainResult analyze(PreprocessedImage preprocessedImage, boolean isSocratic, String subjectScope) {
        VisionStageResult visionStageResult = analyzeVision(preprocessedImage, subjectScope);
        return analyzeReasoning(visionStageResult, isSocratic, subjectScope);
    }

    @Override
    public Map<String, Object> testTextModel() {
        return glmModelClientService.testTextModel(
                aiModelProperties.getModel(),
                "请仅返回字符串: model_test_ok"
        );
    }

    private void validateProvider() {
        if (!aiModelProperties.isEnabled()) {
            throw new IllegalArgumentException("AI 调用已关闭，请检查 app.ai.enabled 配置");
        }

        if (!"zhipu".equalsIgnoreCase(aiModelProperties.getProvider())) {
            throw new IllegalArgumentException("当前仅支持 zhipu provider，实际为: " + aiModelProperties.getProvider());
        }
    }

    private String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "<empty>";
        }
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            return uri.getHost() == null ? baseUrl : uri.getHost();
        } catch (Exception ex) {
            return "<invalid>";
        }
    }
}
