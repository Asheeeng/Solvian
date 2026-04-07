package com.example.springbootbase.service.impl;

import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.entity.DiagnosisRecordEntity;
import com.example.springbootbase.mapper.DiagnosisRecordMapper;
import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.model.ImageHighlight;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.service.ErrorAnalysisService;
import com.example.springbootbase.service.ResponseParserService;
import com.example.springbootbase.service.TagExtractionService;
import com.example.springbootbase.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断结果组装与落库服务。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisResultComposer {

    private final ResponseParserService responseParserService;
    private final TagExtractionService tagExtractionService;
    private final ErrorAnalysisService errorAnalysisService;
    private final DiagnosisRecordMapper diagnosisRecordMapper;
    private final ObjectMapper objectMapper;

    public DiagnosisResult compose(ModelChainResult chainResult, String subjectScope) {
        DiagnosisResult parsed = responseParserService.parse(chainResult.getReasoningRaw());
        parsed.setSubjectScope(subjectScope);
        parsed.setTags(tagExtractionService.extractTags(parsed, chainResult.getReasoningRaw()));
        parsed.setMathData(mergeMathData(parsed.getMathData(), chainResult));

        DiagnosisResult finalResult = errorAnalysisService.analyze(parsed, chainResult.getReasoningRaw());
        finalResult.setImageHighlights(resolveImageHighlights(
                finalResult.getImageHighlights(),
                chainResult.getVisionExtractionResult()
        ));
        return finalResult;
    }

    public EvaluateResponse persistAndBuildResponse(SessionInfo sessionInfo,
                                                    com.example.springbootbase.model.PreprocessedImage preprocessedImage,
                                                    boolean isSocratic,
                                                    DiagnosisResult finalResult,
                                                    String subjectScope) {
        String recordId = IdUtil.newId();
        persistDiagnosisRecord(recordId, sessionInfo, preprocessedImage, isSocratic, finalResult, subjectScope);
        return toEvaluateResponse(recordId, finalResult, isSocratic);
    }

    public EvaluateResponse toEvaluateResponse(String recordId, DiagnosisResult finalResult, boolean isSocratic) {
        return EvaluateResponse.builder()
                .recordId(recordId)
                .status(finalResult.getStatus())
                .steps(finalResult.getSteps())
                .feedback(finalResult.getFeedback())
                .errorIndex(finalResult.getErrorIndex())
                .tags(finalResult.getTags())
                .imageHighlights(finalResult.getImageHighlights())
                .subjectScope(finalResult.getSubjectScope())
                .isMatrixProblem(finalResult.getIsMatrixProblem())
                .diffInfo(finalResult.getDiffInfo())
                .mathData(finalResult.getMathData())
                .isSocratic(isSocratic)
                .build();
    }

    private Map<String, Object> mergeMathData(Map<String, Object> currentMathData, ModelChainResult chainResult) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (currentMathData != null) {
            merged.putAll(currentMathData);
        }
        merged.put("provider", "zhipu");
        merged.put("pipeline", "vision_then_reasoning");
        merged.put("visionModel", chainResult.getVisionModel());
        merged.put("reasoningModel", chainResult.getReasoningModel());
        merged.put("visionRawSnippet", shorten(chainResult.getVisionRaw(), 200));
        merged.put("reasoningRawSnippet", shorten(chainResult.getReasoningRaw(), 200));
        return merged;
    }

    private List<ImageHighlight> resolveImageHighlights(List<ImageHighlight> current, VisionExtractionResult visionExtractionResult) {
        if (current != null && !current.isEmpty()) {
            return current;
        }
        if (visionExtractionResult == null || visionExtractionResult.getImageHighlights() == null) {
            return List.of();
        }
        List<ImageHighlight> resolved = new ArrayList<>();
        for (ImageHighlight item : visionExtractionResult.getImageHighlights()) {
            if (item == null) {
                continue;
            }
            resolved.add(ImageHighlight.builder()
                    .x(item.getX())
                    .y(item.getY())
                    .width(item.getWidth())
                    .height(item.getHeight())
                    .label(item.getLabel())
                    .stepNo(item.getStepNo())
                    .severity(item.getSeverity())
                    .coordinateType(item.getCoordinateType())
                    .mock(item.getMock())
                    .build());
        }
        return resolved;
    }

    private void persistDiagnosisRecord(String recordId,
                                        SessionInfo sessionInfo,
                                        com.example.springbootbase.model.PreprocessedImage preprocessedImage,
                                        boolean isSocratic,
                                        DiagnosisResult finalResult,
                                        String subjectScope) {
        DiagnosisRecordEntity entity = DiagnosisRecordEntity.builder()
                .recordId(recordId)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole().name())
                .status(finalResult.getStatus())
                .stepsJson(toJson(toStepSummaries(finalResult.getSteps())))
                .feedback(finalResult.getFeedback())
                .errorIndex(finalResult.getErrorIndex())
                .tagsJson(toJson(finalResult.getTags() == null ? List.of() : new ArrayList<>(finalResult.getTags())))
                .isSocratic(isSocratic)
                .problemType(subjectScope)
                .imageName(preprocessedImage.getFileName())
                .mathDataJson(toJson(finalResult.getMathData()))
                .createdAt(OffsetDateTime.now())
                .build();
        diagnosisRecordMapper.insert(entity);
    }

    private List<String> toStepSummaries(List<DiagnosisStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of("未返回步骤信息");
        }
        List<String> summaries = new ArrayList<>();
        for (DiagnosisStep step : steps) {
            String title = step.getTitle() == null ? "" : step.getTitle();
            String content = step.getContent() == null ? "" : step.getContent();
            String latex = step.getLatex() == null || step.getLatex().isBlank() ? "" : " [" + step.getLatex() + "]";
            summaries.add(title + ": " + content + latex);
        }
        return summaries;
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化诊断记录失败", e);
        }
    }

    private String shorten(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= max ? raw : raw.substring(0, max);
    }
}
