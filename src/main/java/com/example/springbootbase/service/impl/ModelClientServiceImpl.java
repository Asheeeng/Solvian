package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.model.VisionStageResult;
import com.example.springbootbase.service.ModelClientService;
import com.example.springbootbase.service.PromptBuilderService;
import com.example.springbootbase.service.VisionExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final ObjectMapper objectMapper;

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

        if (shouldUseFastFallback(visionStageResult.getVisionExtractionResult())) {
            return ModelChainResult.builder()
                    .visionModel(visionStageResult.getVisionModel())
                    .reasoningModel("rule-fallback")
                    .visionRaw(visionStageResult.getVisionRaw())
                    .reasoningRaw(buildFallbackReasoningJson(visionStageResult.getVisionExtractionResult(), subjectScope))
                    .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                    .build();
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
            log.warn("[reasoning-stage] fallback to rule-based result, reason={}", ex.getMessage());
            return ModelChainResult.builder()
                    .visionModel(visionStageResult.getVisionModel())
                    .reasoningModel("rule-fallback")
                    .visionRaw(visionStageResult.getVisionRaw())
                    .reasoningRaw(buildTimeoutFallbackReasoningJson(
                            visionStageResult.getVisionExtractionResult(),
                            subjectScope,
                            ex.getMessage()
                    ))
                    .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                    .build();
        }

        if (reasoningRaw == null || reasoningRaw.isBlank()) {
            log.warn("[reasoning-stage] empty response, fallback to rule-based result");
            return ModelChainResult.builder()
                    .visionModel(visionStageResult.getVisionModel())
                    .reasoningModel("rule-fallback")
                    .visionRaw(visionStageResult.getVisionRaw())
                    .reasoningRaw(buildTimeoutFallbackReasoningJson(
                            visionStageResult.getVisionExtractionResult(),
                            subjectScope,
                            "empty_response"
                    ))
                    .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                    .build();
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

    private boolean shouldUseFastFallback(VisionExtractionResult result) {
        if (result == null) {
            return true;
        }

        boolean noProblemText = isBlank(result.getProblemText());
        boolean noSteps = result.getStudentSteps() == null || result.getStudentSteps().isEmpty();
        boolean noMatrix = result.getMatrixExpressions() == null || result.getMatrixExpressions().isEmpty();
        boolean flaggedNonMatrix = Boolean.FALSE.equals(result.getIsMatrixProblem());
        boolean veryLowConfidence = result.getConfidence() != null && result.getConfidence() <= 0.15d;

        return flaggedNonMatrix || (noProblemText && noSteps && noMatrix) || (veryLowConfidence && noSteps && noMatrix);
    }

    private String buildFallbackReasoningJson(VisionExtractionResult result, String subjectScope) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "unable_to_judge");
        payload.put("steps", List.of(Map.of(
                "stepNo", 1,
                "title", "题图信息不足",
                "content", "当前题图中未识别出足够清晰的矩阵题目或解题步骤，无法继续进行严格批改。",
                "latex", "",
                "highlightedLatex", "",
                "isWrong", false,
                "explanation", "",
                "latexHighlights", List.of(),
                "matrixCellDiffs", List.of()
        )));
        payload.put("errorIndex", null);
        payload.put("feedback", buildFallbackFeedback(result));
        payload.put("tags", List.of("#矩阵初等变换"));
        payload.put("imageHighlights", result.getImageHighlights() == null ? List.of() : result.getImageHighlights());
        payload.put("diffInfo", Map.of(
                "summary", "题图内容不足，暂时无法比较标准结果与学生作答",
                "type", "insufficient_visual_data"
        ));
        payload.put("subjectScope", isBlank(subjectScope) ? "matrix" : subjectScope);
        payload.put("isMatrixProblem", Boolean.TRUE.equals(result.getIsMatrixProblem()));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("生成兜底推理结果失败", ex);
        }
    }

    private String buildTimeoutFallbackReasoningJson(VisionExtractionResult result, String subjectScope, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "unable_to_judge");
        payload.put("steps", buildTimeoutFallbackSteps(result));
        payload.put("errorIndex", null);
        payload.put("feedback", buildTimeoutFallbackFeedback(result, reason));
        payload.put("tags", List.of("#矩阵初等变换"));
        payload.put("imageHighlights", result == null || result.getImageHighlights() == null ? List.of() : result.getImageHighlights());
        payload.put("diffInfo", Map.of(
                "summary", "已识别到题图内容，但推理模型本次响应超时，当前先返回视觉识别结果供老师复核",
                "type", "reasoning_timeout"
        ));
        payload.put("subjectScope", isBlank(subjectScope) ? "matrix" : subjectScope);
        payload.put("isMatrixProblem", result == null || result.getIsMatrixProblem() == null || result.getIsMatrixProblem());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("生成超时兜底结果失败", ex);
        }
    }

    private List<Map<String, Object>> buildTimeoutFallbackSteps(VisionExtractionResult result) {
        if (result == null) {
            return List.of(Map.of(
                    "stepNo", 1,
                    "title", "识别结果暂不可用",
                    "content", "当前没有可展示的视觉识别结果。",
                    "latex", "",
                    "highlightedLatex", "",
                    "isWrong", false,
                    "explanation", "",
                    "latexHighlights", List.of(),
                    "matrixCellDiffs", List.of()
            ));
        }

        java.util.ArrayList<Map<String, Object>> steps = new java.util.ArrayList<>();
        List<String> studentSteps = result.getStudentSteps() == null ? List.of() : result.getStudentSteps();
        List<String> matrixExpressions = result.getMatrixExpressions() == null ? List.of() : result.getMatrixExpressions();

        int stepNo = 1;
        for (String step : studentSteps.stream().limit(3).toList()) {
            steps.add(Map.of(
                    "stepNo", stepNo++,
                    "title", "视觉识别步骤 " + (stepNo - 1),
                    "content", shortenForFallback(step, 240),
                    "latex", "",
                    "highlightedLatex", "",
                    "isWrong", false,
                    "explanation", "当前先展示视觉识别结果，推理模型本次未及时返回。",
                    "latexHighlights", List.of(),
                    "matrixCellDiffs", List.of()
            ));
        }

        for (String expr : matrixExpressions.stream().limit(Math.max(0, 3 - steps.size())).toList()) {
            steps.add(Map.of(
                    "stepNo", stepNo++,
                    "title", "识别到的矩阵表达式",
                    "content", shortenForFallback(expr, 240),
                    "latex", shortenForFallback(expr, 240),
                    "highlightedLatex", "",
                    "isWrong", false,
                    "explanation", "当前先展示视觉识别结果，推理模型本次未及时返回。",
                    "latexHighlights", List.of(),
                    "matrixCellDiffs", List.of()
            ));
        }

        if (steps.isEmpty()) {
            steps.add(Map.of(
                    "stepNo", 1,
                    "title", "视觉识别摘要",
                    "content", shortenForFallback(result.getRawSummary(), 240),
                    "latex", "",
                    "highlightedLatex", "",
                    "isWrong", false,
                    "explanation", "当前先展示视觉识别摘要，推理模型本次未及时返回。",
                    "latexHighlights", List.of(),
                    "matrixCellDiffs", List.of()
            ));
        }

        return steps;
    }

    private String buildTimeoutFallbackFeedback(VisionExtractionResult result, String reason) {
        String detail = isBlank(reason) ? "推理模型本次未及时返回" : "推理模型本次响应超时";
        if (result == null) {
            return detail + "，请稍后重试。";
        }

        String summary = result.getRawSummary();
        if (summary == null || summary.isBlank()) {
            return "已识别到题图内容，但" + detail + "。当前先返回视觉识别结果供老师预览，建议稍后重新发起诊断。";
        }
        return summary + "。但" + detail + "，当前先返回视觉识别结果供老师预览，建议稍后重新发起诊断。";
    }

    private String buildFallbackFeedback(VisionExtractionResult result) {
        if (result == null) {
            return "当前没有可用的题图解析结果，请上传包含题目与解题过程的清晰作业图片后重试。";
        }

        String summary = result.getRawSummary();
        if (summary == null || summary.isBlank()) {
            return "当前没有识别出清晰的矩阵题目内容或学生步骤，请上传包含题目与完整解题过程的清晰图片后重试。";
        }
        return summary + "。请上传包含题目与完整解题过程的清晰图片后重试。";
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private String shortenForFallback(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
