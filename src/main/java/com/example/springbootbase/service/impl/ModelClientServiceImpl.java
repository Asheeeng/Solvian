package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;
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
    public ModelChainResult analyze(PreprocessedImage preprocessedImage, boolean isSocratic, String subjectScope) {
        if (!aiModelProperties.isEnabled()) {
            throw new IllegalArgumentException("AI 调用已关闭，请检查 app.ai.enabled 配置");
        }

        if (!"zhipu".equalsIgnoreCase(aiModelProperties.getProvider())) {
            throw new IllegalArgumentException("当前仅支持 zhipu provider，实际为: " + aiModelProperties.getProvider());
        }

        if (aiModelProperties.isMockEnabled()) {
            log.warn("[model-chain] mockEnabled=true，当前走 mock 实现（仅用于本地调试）");
            String raw = mockModelClientService.analyze(preprocessedImage, isSocratic, subjectScope, "mock-enabled=true");
            VisionExtractionResult fallbackVision = VisionExtractionResult.builder()
                    .problemText("mock mode")
                    .isMatrixProblem(true)
                    .confidence(1.0d)
                    .build();
            return ModelChainResult.builder()
                    .visionModel("mock-vision")
                    .reasoningModel("mock-reasoning")
                    .visionRaw("{\"problemText\":\"mock mode\"}")
                    .reasoningRaw(raw)
                    .visionExtractionResult(fallbackVision)
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

        VisionExtractionResult visionExtractionResult = visionExtractionService.parse(visionRaw);
        String reasoningPrompt = promptBuilderService.buildReasoningPrompt(
                visionExtractionResult,
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
                .visionModel(aiModelProperties.getVisionModel())
                .reasoningModel(aiModelProperties.getModel())
                .visionRaw(visionRaw)
                .reasoningRaw(reasoningRaw)
                .visionExtractionResult(visionExtractionResult)
                .build();
    }

    @Override
    public Map<String, Object> testTextModel() {
        return glmModelClientService.testTextModel(
                aiModelProperties.getModel(),
                "请仅返回字符串: model_test_ok"
        );
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

