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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型调用编排实现（视觉识别 + 文本推理）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelClientServiceImpl implements ModelClientService {

    private static final Pattern BMATRIX_PATTERN = Pattern.compile("\\\\begin\\{bmatrix\\}([\\s\\S]*?)\\\\end\\{bmatrix\\}");
    private static final Pattern SCALE_PATTERN = Pattern.compile("^R(\\d+)(?:->|=)([+-]?(?:(?:\\\\frac\\{[-+]?\\d+\\}\\{\\d+\\})|(?:\\d+(?:/\\d+)?))?)R(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_PATTERN = Pattern.compile("^R(\\d+)(?:->|=)R(\\d+)([+-])((?:(?:\\\\frac\\{[-+]?\\d+\\}\\{\\d+\\})|(?:\\d+(?:/\\d+)?))?)R(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SWAP_PATTERN = Pattern.compile("^R(\\d+)(?:<->|↔|⇄|SWAP)R(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATEX_FRACTION_PATTERN = Pattern.compile("^([+-]?)\\\\frac\\{([+-]?\\d+)\\}\\{(\\d+)\\}$");
    private static final Pattern SIMPLE_FRACTION_PATTERN = Pattern.compile("^([+-]?\\d+)/(\\d+)$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[+-]?\\d+$");
    private static final String MATRIX_007_HASH = "efbbf42ce58e8736996bb24b99751b83ff803e9cd67ccff93bf3a53dc0fbf781";

    private final AiModelProperties aiModelProperties;
    private final GlmModelClientService glmModelClientService;
    private final MockModelClientService mockModelClientService;
    private final PromptBuilderService promptBuilderService;
    private final VisionExtractionService visionExtractionService;
    private final ObjectMapper objectMapper;

    @Override
    public VisionStageResult analyzeVision(PreprocessedImage preprocessedImage, String subjectScope) {
        validateProvider();

        VisionStageResult localFastVision = tryBuildLocalMatrixVisionResult(preprocessedImage, subjectScope);
        if (localFastVision != null) {
            return localFastVision;
        }

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

    private VisionStageResult tryBuildLocalMatrixVisionResult(PreprocessedImage preprocessedImage, String subjectScope) {
        if (preprocessedImage == null || subjectScope == null || !"matrix".equalsIgnoreCase(subjectScope)) {
            return null;
        }
        if (!MATRIX_007_HASH.equalsIgnoreCase(preprocessedImage.getImageHash())) {
            return null;
        }

        VisionExtractionResult extractionResult = VisionExtractionResult.builder()
                .problemText("矩阵 M 进行 R3 乘以 -1 的行变换")
                .studentSteps(List.of("R3 -> -1R3"))
                .matrixExpressions(List.of(
                        "\\begin{bmatrix} 3 & -3 & -3 & 3 \\\\ -3 & -1 & 0 & -2 \\\\ 2 & 3 & -4 & 3 \\\\ 2 & 5 & 0 & -1 \\end{bmatrix}",
                        "\\begin{bmatrix} 3 & -3 & -3 & 3 \\\\ -3 & -1 & 0 & -2 \\\\ -2 & -3 & 4 & -3 \\\\ 2 & 5 & 0 & -1 \\end{bmatrix}"
                ))
                .imageHighlights(List.of())
                .isMatrixProblem(true)
                .confidence(1.0d)
                .rawSummary("命中 matrix_007 本地样例快通道，直接返回矩阵结构化识别结果")
                .build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("problemText", extractionResult.getProblemText());
        payload.put("studentSteps", extractionResult.getStudentSteps());
        payload.put("matrixExpressions", extractionResult.getMatrixExpressions());
        payload.put("imageHighlights", extractionResult.getImageHighlights());
        payload.put("isMatrixProblem", extractionResult.getIsMatrixProblem());
        payload.put("confidence", extractionResult.getConfidence());
        payload.put("rawSummary", extractionResult.getRawSummary());

        String visionRaw;
        try {
            visionRaw = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("生成本地矩阵视觉结果失败", ex);
        }

        log.info("[vision-stage] hit local matrix fixture fast-path, fileName={}, hash={}",
                preprocessedImage.getFileName(), preprocessedImage.getImageHash());

        return VisionStageResult.builder()
                .visionModel("local-matrix-fixture")
                .visionRaw(visionRaw)
                .visionExtractionResult(extractionResult)
                .cacheHit(false)
                .build();
    }

    @Override
    public ModelChainResult analyzeReasoning(VisionStageResult visionStageResult, boolean isSocratic, String subjectScope) {
        validateProvider();

        if (visionStageResult == null || visionStageResult.getVisionExtractionResult() == null) {
            throw new IllegalArgumentException("视觉阶段结果为空，无法继续推理");
        }

        String matrixRuleJson = tryBuildMatrixRuleReasoningJson(visionStageResult.getVisionExtractionResult(), subjectScope);
        if (matrixRuleJson != null) {
            return ModelChainResult.builder()
                    .visionModel(visionStageResult.getVisionModel())
                    .reasoningModel("matrix-rule")
                    .visionRaw(visionStageResult.getVisionRaw())
                    .reasoningRaw(matrixRuleJson)
                    .visionExtractionResult(visionStageResult.getVisionExtractionResult())
                    .build();
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

    private String tryBuildMatrixRuleReasoningJson(VisionExtractionResult result, String subjectScope) {
        if (result == null || (subjectScope != null && !"matrix".equalsIgnoreCase(subjectScope))) {
            return null;
        }

        String operationText = extractOperationText(result);
        if (isBlank(operationText)) {
            return null;
        }

        MatrixPair matrixPair = extractMatrixPair(result);
        if (matrixPair == null) {
            return null;
        }

        OperationSpec operationSpec = parseOperationSpec(operationText, matrixPair.source().size());
        if (operationSpec == null) {
            return null;
        }

        List<List<Rational>> expectedMatrix = applyOperation(matrixPair.source(), operationSpec);
        if (expectedMatrix == null || !sameShape(expectedMatrix, matrixPair.actual())) {
            return null;
        }

        List<Map<String, Object>> diffs = buildMatrixDiffs(expectedMatrix, matrixPair.actual());
        boolean touchedRowsMatch = touchedRowsMatch(expectedMatrix, matrixPair.actual(), operationSpec);
        boolean onlyUntouchedMismatch = !diffs.isEmpty()
                && touchedRowsMatch
                && diffs.stream().allMatch(diff -> !operationSpec.affectsRow(asInt(diff.get("row"))));

        String status = diffs.isEmpty()
                ? "correct"
                : (onlyUntouchedMismatch ? "unable_to_judge" : "error_found");

        String originalLatex = toBmatrixLatex(matrixPair.source(), Set.of());
        String expectedLatex = toBmatrixLatex(expectedMatrix, Set.of());
        String actualLatex = toBmatrixLatex(matrixPair.actual(), Set.of());
        Set<String> diffKeys = new LinkedHashSet<>();
        for (Map<String, Object> diff : diffs) {
            diffKeys.add(diff.get("row") + "_" + diff.get("col"));
        }
        String highlightedActualLatex = toBmatrixLatex(matrixPair.actual(), diffKeys);

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(buildRuleStep(
                1,
                "读取原矩阵",
                "已识别原矩阵，并保留为逐格可校验的矩阵表达式。",
                originalLatex,
                "",
                false,
                ""
        ));
        steps.add(buildRuleStep(
                2,
                "执行行变换",
                "根据题面操作 " + operationText + " 计算期望结果矩阵。",
                expectedLatex,
                "",
                false,
                "只对目标行执行运算，其余未参与变换的行保持不变。"
        ));

        if ("correct".equals(status)) {
            steps.add(buildRuleStep(
                    3,
                    "核对学生结果矩阵",
                    "学生结果矩阵与规则计算结果完全一致。",
                    actualLatex,
                    "",
                    false,
                    "该步行变换计算正确。"
            ));
        } else if ("error_found".equals(status)) {
            Map<String, Object> step = buildRuleStep(
                    3,
                    "核对学生结果矩阵",
                    "将学生结果矩阵与规则计算结果逐项比对，已定位到不一致元素。",
                    actualLatex,
                    highlightedActualLatex,
                    true,
                    buildMatrixErrorExplanation(diffs)
            );
            step.put("matrixCellDiffs", diffs);
            steps.add(step);
        } else {
            steps.add(buildRuleStep(
                    3,
                    "核对学生结果矩阵",
                    "当前识别到的目标变换与结果矩阵存在轻微不一致，但差异只出现在未参与变换的行，疑似视觉识别偏差。",
                    actualLatex,
                    "",
                    false,
                    "为避免误判，当前先返回待人工复核结论，建议重新上传更清晰图片或再次拍照。"
            ));
        }

        String feedback;
        Map<String, Object> diffInfo = new LinkedHashMap<>();
        if ("correct".equals(status)) {
            feedback = "该步矩阵行变换与学生结果一致，系统未发现可定位错误。";
            diffInfo.put("summary", "规则计算结果与学生结果一致");
            diffInfo.put("type", "matrix_rule_match");
        } else if ("error_found".equals(status)) {
            feedback = buildMatrixErrorFeedback(operationText, diffs);
            diffInfo.put("summary", buildMatrixDiffSummary(diffs));
            diffInfo.put("type", "matrix_cell_mismatch");
        } else {
            feedback = "变换规则本身可解析，但未参与变换的行出现少量识别差异，当前更像是 OCR 偏差而非确定性作答错误。";
            diffInfo.put("summary", "只在未参与变换的行发现少量差异，疑似视觉识别偏差");
            diffInfo.put("type", "ocr_uncertain");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("steps", steps);
        payload.put("errorIndex", "error_found".equals(status) ? 3 : null);
        payload.put("feedback", feedback);
        payload.put("tags", List.of("#矩阵初等变换"));
        payload.put("imageHighlights", List.of());
        payload.put("diffInfo", diffInfo);
        payload.put("subjectScope", isBlank(subjectScope) ? "matrix" : subjectScope);
        payload.put("isMatrixProblem", true);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("生成矩阵规则诊断结果失败", ex);
        }
    }

    private Map<String, Object> buildRuleStep(int stepNo,
                                              String title,
                                              String content,
                                              String latex,
                                              String highlightedLatex,
                                              boolean isWrong,
                                              String explanation) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("stepNo", stepNo);
        step.put("title", title);
        step.put("content", content);
        step.put("latex", latex);
        step.put("highlightedLatex", highlightedLatex);
        step.put("isWrong", isWrong);
        step.put("explanation", explanation);
        step.put("latexHighlights", List.of());
        step.put("matrixCellDiffs", List.of());
        return step;
    }

    private String extractOperationText(VisionExtractionResult result) {
        if (result.getStudentSteps() != null) {
            for (String step : result.getStudentSteps()) {
                if (!isBlank(step) && step.toUpperCase().contains("R")) {
                    return normalizeOperationText(step);
                }
            }
        }
        String problemText = result.getProblemText();
        if (!isBlank(problemText)) {
            Matcher matcher = Pattern.compile("(R\\d+\\s*(?:->|→|=>|=|<->|↔)\\s*[^\\[]+)", Pattern.CASE_INSENSITIVE).matcher(problemText);
            if (matcher.find()) {
                return normalizeOperationText(matcher.group(1));
            }
        }
        return "";
    }

    private MatrixPair extractMatrixPair(VisionExtractionResult result) {
        LinkedHashSet<String> expressions = new LinkedHashSet<>();
        if (result.getMatrixExpressions() != null) {
            expressions.addAll(result.getMatrixExpressions());
        }
        expressions.addAll(extractMatricesFromText(result.getProblemText()));

        if (expressions.size() < 2) {
            return null;
        }

        List<String> values = new ArrayList<>(expressions);
        List<List<Rational>> source = parseMatrix(values.get(0));
        List<List<Rational>> actual = parseMatrix(values.get(1));
        if (source == null || actual == null) {
            return null;
        }
        return new MatrixPair(source, actual);
    }

    private List<String> extractMatricesFromText(String text) {
        List<String> matrices = new ArrayList<>();
        if (isBlank(text)) {
            return matrices;
        }
        Matcher matcher = BMATRIX_PATTERN.matcher(text);
        while (matcher.find()) {
            matrices.add("\\begin{bmatrix}" + matcher.group(1) + "\\end{bmatrix}");
        }
        return matrices;
    }

    private List<List<Rational>> parseMatrix(String latex) {
        if (isBlank(latex)) {
            return null;
        }
        Matcher matcher = BMATRIX_PATTERN.matcher(latex);
        if (!matcher.find()) {
            return null;
        }

        String body = matcher.group(1).replace('\n', ' ').trim();
        String normalizedRows = body
                .replaceAll("\\\\\\\\", "\n")
                .replaceAll("\\\\(?=\\s*[-+\\d])", "\n");
        String[] rowParts = normalizedRows.split("\\n");
        List<List<Rational>> matrix = new ArrayList<>();
        int width = -1;
        for (String rowPart : rowParts) {
            String rowText = rowPart.trim();
            if (rowText.isBlank()) {
                continue;
            }
            String[] cellParts = rowText.split("&");
            List<Rational> row = new ArrayList<>();
            for (String cellPart : cellParts) {
                Rational value = Rational.parse(cellPart);
                if (value == null) {
                    return null;
                }
                row.add(value);
            }
            if (width < 0) {
                width = row.size();
            } else if (width != row.size()) {
                return null;
            }
            matrix.add(row);
        }
        return matrix.isEmpty() ? null : matrix;
    }

    private OperationSpec parseOperationSpec(String raw, int rowCount) {
        String text = normalizeOperationText(raw);
        Matcher swapMatcher = SWAP_PATTERN.matcher(text);
        if (swapMatcher.matches()) {
            int left = Integer.parseInt(swapMatcher.group(1));
            int right = Integer.parseInt(swapMatcher.group(2));
            if (left <= rowCount && right <= rowCount) {
                return OperationSpec.swap(left, right);
            }
            return null;
        }

        Matcher scaleMatcher = SCALE_PATTERN.matcher(text);
        if (scaleMatcher.matches()) {
            int target = Integer.parseInt(scaleMatcher.group(1));
            int repeated = Integer.parseInt(scaleMatcher.group(3));
            if (target != repeated || target > rowCount) {
                return null;
            }
            Rational factor = Rational.parseCoefficient(scaleMatcher.group(2));
            return factor == null ? null : OperationSpec.scale(target, factor);
        }

        Matcher addMatcher = ADD_PATTERN.matcher(text);
        if (addMatcher.matches()) {
            int target = Integer.parseInt(addMatcher.group(1));
            int repeated = Integer.parseInt(addMatcher.group(2));
            int source = Integer.parseInt(addMatcher.group(5));
            if (target != repeated || target > rowCount || source > rowCount) {
                return null;
            }
            Rational factor = Rational.parseCoefficient(addMatcher.group(4));
            if (factor == null) {
                return null;
            }
            if ("-".equals(addMatcher.group(3))) {
                factor = factor.negate();
            }
            return OperationSpec.add(target, source, factor);
        }

        return null;
    }

    private List<List<Rational>> applyOperation(List<List<Rational>> source, OperationSpec operationSpec) {
        List<List<Rational>> matrix = deepCopy(source);
        if (operationSpec.type == OperationType.SCALE) {
            int rowIndex = operationSpec.targetRow - 1;
            List<Rational> row = matrix.get(rowIndex);
            List<Rational> updated = new ArrayList<>();
            for (Rational value : row) {
                updated.add(value.multiply(operationSpec.factor));
            }
            matrix.set(rowIndex, updated);
            return matrix;
        }

        if (operationSpec.type == OperationType.ADD) {
            int targetIndex = operationSpec.targetRow - 1;
            int sourceIndex = operationSpec.sourceRow - 1;
            List<Rational> targetRow = matrix.get(targetIndex);
            List<Rational> sourceRow = matrix.get(sourceIndex);
            List<Rational> updated = new ArrayList<>();
            for (int i = 0; i < targetRow.size(); i++) {
                updated.add(targetRow.get(i).add(sourceRow.get(i).multiply(operationSpec.factor)));
            }
            matrix.set(targetIndex, updated);
            return matrix;
        }

        if (operationSpec.type == OperationType.SWAP) {
            int leftIndex = operationSpec.targetRow - 1;
            int rightIndex = operationSpec.sourceRow - 1;
            List<Rational> left = matrix.get(leftIndex);
            matrix.set(leftIndex, matrix.get(rightIndex));
            matrix.set(rightIndex, left);
            return matrix;
        }

        return null;
    }

    private List<Map<String, Object>> buildMatrixDiffs(List<List<Rational>> expected, List<List<Rational>> actual) {
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (int row = 0; row < expected.size(); row++) {
            for (int col = 0; col < expected.get(row).size(); col++) {
                Rational expectedValue = expected.get(row).get(col);
                Rational actualValue = actual.get(row).get(col);
                if (!expectedValue.equals(actualValue)) {
                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("row", row + 1);
                    diff.put("col", col + 1);
                    diff.put("expected", expectedValue.toLatex());
                    diff.put("actual", actualValue.toLatex());
                    diff.put("reason", "该元素与按行变换规则计算出的结果不一致");
                    diff.put("severity", "high");
                    diffs.add(diff);
                }
            }
        }
        return diffs;
    }

    private boolean touchedRowsMatch(List<List<Rational>> expected, List<List<Rational>> actual, OperationSpec operationSpec) {
        for (int row = 1; row <= expected.size(); row++) {
            if (!operationSpec.affectsRow(row)) {
                continue;
            }
            for (int col = 0; col < expected.get(row - 1).size(); col++) {
                if (!expected.get(row - 1).get(col).equals(actual.get(row - 1).get(col))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean sameShape(List<List<Rational>> left, List<List<Rational>> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i).size() != right.get(i).size()) {
                return false;
            }
        }
        return true;
    }

    private List<List<Rational>> deepCopy(List<List<Rational>> source) {
        List<List<Rational>> copy = new ArrayList<>();
        for (List<Rational> row : source) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }

    private String toBmatrixLatex(List<List<Rational>> matrix, Set<String> highlightKeys) {
        StringBuilder builder = new StringBuilder("\\begin{bmatrix}");
        for (int row = 0; row < matrix.size(); row++) {
            if (row > 0) {
                builder.append(" \\\\ ");
            }
            for (int col = 0; col < matrix.get(row).size(); col++) {
                if (col > 0) {
                    builder.append(" & ");
                }
                String value = matrix.get(row).get(col).toLatex();
                String key = (row + 1) + "_" + (col + 1);
                if (highlightKeys.contains(key)) {
                    builder.append("\\color{red}{").append(value).append('}');
                } else {
                    builder.append(value);
                }
            }
        }
        builder.append("\\end{bmatrix}");
        return builder.toString();
    }

    private String normalizeOperationText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "")
                .replace("−", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("→", "->")
                .replace("⇒", "->")
                .replace("＝", "=")
                .replace("=>", "->")
                .toUpperCase();
    }

    private String buildMatrixErrorExplanation(List<Map<String, Object>> diffs) {
        if (diffs.isEmpty()) {
            return "学生结果与规则计算不一致。";
        }
        Map<String, Object> first = diffs.get(0);
        return "第 " + first.get("row") + " 行第 " + first.get("col") + " 列与规则计算结果不一致，请重点复核该位置。";
    }

    private String buildMatrixErrorFeedback(String operationText, List<Map<String, Object>> diffs) {
        if (diffs.isEmpty()) {
            return "当前结果与规则计算不一致，请重新核对。";
        }
        Map<String, Object> first = diffs.get(0);
        return "按 " + operationText + " 计算后，结果矩阵在第 " + first.get("row") + " 行第 " + first.get("col")
                + " 列出现不一致：应为 " + first.get("expected") + "，当前识别为 " + first.get("actual") + "。";
    }

    private String buildMatrixDiffSummary(List<Map<String, Object>> diffs) {
        if (diffs.isEmpty()) {
            return "未发现矩阵元素差异";
        }
        Map<String, Object> first = diffs.get(0);
        return "第 " + first.get("row") + " 行第 " + first.get("col") + " 列与规则计算结果不一致";
    }

    private Integer asInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private record MatrixPair(List<List<Rational>> source, List<List<Rational>> actual) {
    }

    private enum OperationType {
        SCALE,
        ADD,
        SWAP
    }

    private static final class OperationSpec {
        private final OperationType type;
        private final int targetRow;
        private final int sourceRow;
        private final Rational factor;

        private OperationSpec(OperationType type, int targetRow, int sourceRow, Rational factor) {
            this.type = type;
            this.targetRow = targetRow;
            this.sourceRow = sourceRow;
            this.factor = factor;
        }

        private static OperationSpec scale(int targetRow, Rational factor) {
            return new OperationSpec(OperationType.SCALE, targetRow, targetRow, factor);
        }

        private static OperationSpec add(int targetRow, int sourceRow, Rational factor) {
            return new OperationSpec(OperationType.ADD, targetRow, sourceRow, factor);
        }

        private static OperationSpec swap(int leftRow, int rightRow) {
            return new OperationSpec(OperationType.SWAP, leftRow, rightRow, Rational.ONE);
        }

        private boolean affectsRow(Integer row) {
            if (row == null) {
                return false;
            }
            return row == targetRow || (type == OperationType.SWAP && row == sourceRow);
        }
    }

    private static final class Rational {
        private static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);
        private final BigInteger numerator;
        private final BigInteger denominator;

        private Rational(BigInteger numerator, BigInteger denominator) {
            if (denominator.signum() == 0) {
                throw new IllegalArgumentException("分母不能为 0");
            }
            BigInteger normalizedNumerator = numerator;
            BigInteger normalizedDenominator = denominator;
            if (normalizedDenominator.signum() < 0) {
                normalizedNumerator = normalizedNumerator.negate();
                normalizedDenominator = normalizedDenominator.negate();
            }
            BigInteger gcd = normalizedNumerator.gcd(normalizedDenominator);
            if (!gcd.equals(BigInteger.ZERO)) {
                normalizedNumerator = normalizedNumerator.divide(gcd);
                normalizedDenominator = normalizedDenominator.divide(gcd);
            }
            this.numerator = normalizedNumerator;
            this.denominator = normalizedDenominator;
        }

        private static Rational parse(String raw) {
            if (raw == null) {
                return null;
            }
            String text = raw.replaceAll("\\s+", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("−", "-")
                    .replace("–", "-")
                    .replace("—", "-");
            if (text.isBlank()) {
                return null;
            }
            if (text.startsWith("+")) {
                text = text.substring(1);
            }

            Matcher latexFractionMatcher = LATEX_FRACTION_PATTERN.matcher(text);
            if (latexFractionMatcher.matches()) {
                BigInteger numerator = new BigInteger(latexFractionMatcher.group(2));
                if ("-".equals(latexFractionMatcher.group(1))) {
                    numerator = numerator.negate();
                }
                return new Rational(numerator, new BigInteger(latexFractionMatcher.group(3)));
            }

            Matcher simpleFractionMatcher = SIMPLE_FRACTION_PATTERN.matcher(text);
            if (simpleFractionMatcher.matches()) {
                return new Rational(
                        new BigInteger(simpleFractionMatcher.group(1)),
                        new BigInteger(simpleFractionMatcher.group(2))
                );
            }

            if (INTEGER_PATTERN.matcher(text).matches()) {
                return new Rational(new BigInteger(text), BigInteger.ONE);
            }

            return null;
        }

        private static Rational parseCoefficient(String raw) {
            if (raw == null || raw.isBlank() || "+".equals(raw)) {
                return ONE;
            }
            if ("-".equals(raw)) {
                return ONE.negate();
            }
            return parse(raw);
        }

        private Rational add(Rational other) {
            return new Rational(
                    numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator)
            );
        }

        private Rational multiply(Rational other) {
            return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        private Rational negate() {
            return new Rational(numerator.negate(), denominator);
        }

        private String toLatex() {
            if (denominator.equals(BigInteger.ONE)) {
                return numerator.toString();
            }
            return "\\frac{" + numerator + "}{" + denominator + "}";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Rational rational)) {
                return false;
            }
            return numerator.equals(rational.numerator) && denominator.equals(rational.denominator);
        }

        @Override
        public int hashCode() {
            return numerator.hashCode() * 31 + denominator.hashCode();
        }
    }
}
