package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.model.DiagnosisRecord;
import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.service.AiDiagnosisService;
import com.example.springbootbase.service.ErrorAnalysisService;
import com.example.springbootbase.service.ImagePreprocessService;
import com.example.springbootbase.service.ModelClientService;
import com.example.springbootbase.service.ResponseParserService;
import com.example.springbootbase.service.TagExtractionService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 诊断编排实现（老师端矩阵题）。
 */
@Service
@RequiredArgsConstructor
public class AiDiagnosisServiceImpl implements AiDiagnosisService {

    private final ImagePreprocessService imagePreprocessService;
    private final ModelClientService modelClientService;
    private final ResponseParserService responseParserService;
    private final TagExtractionService tagExtractionService;
    private final ErrorAnalysisService errorAnalysisService;
    private final InMemoryDataStore store;

    @Override
    public EvaluateResponse evaluate(MultipartFile file, boolean isSocratic, String problemType, SessionInfo sessionInfo) {
        validateTeacherScope(sessionInfo);
        String subjectScope = normalizeSubjectScope(problemType);

        PreprocessedImage preprocessedImage = imagePreprocessService.preprocess(file);
        ModelChainResult chainResult = modelClientService.analyze(preprocessedImage, isSocratic, subjectScope);

        DiagnosisResult parsed = responseParserService.parse(chainResult.getReasoningRaw());
        parsed.setSubjectScope(subjectScope);
        parsed.setTags(tagExtractionService.extractTags(parsed, chainResult.getReasoningRaw()));
        parsed.setMathData(mergeMathData(parsed.getMathData(), chainResult));

        DiagnosisResult finalResult = errorAnalysisService.analyze(parsed, chainResult.getReasoningRaw());

        String recordId = IdUtil.newId();
        DiagnosisRecord record = DiagnosisRecord.builder()
                .recordId(recordId)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole())
                .fileName(preprocessedImage.getFileName())
                .isSocratic(isSocratic)
                .status(finalResult.getStatus())
                .steps(toStepSummaries(finalResult.getSteps()))
                .feedback(finalResult.getFeedback())
                .errorIndex(finalResult.getErrorIndex())
                .tags(new ArrayList<>(finalResult.getTags()))
                .mathData(finalResult.getMathData())
                .createdAt(TimeUtil.now())
                .build();

        store.getDiagnosisById().put(recordId, record);
        store.getDiagnosisOrder().add(0, recordId);

        return EvaluateResponse.builder()
                .recordId(recordId)
                .status(finalResult.getStatus())
                .steps(finalResult.getSteps())
                .feedback(finalResult.getFeedback())
                .errorIndex(finalResult.getErrorIndex())
                .tags(finalResult.getTags())
                .subjectScope(finalResult.getSubjectScope())
                .isMatrixProblem(finalResult.getIsMatrixProblem())
                .mathData(finalResult.getMathData())
                .isSocratic(isSocratic)
                .build();
    }

    private void validateTeacherScope(SessionInfo sessionInfo) {
        if (sessionInfo == null) {
            throw new IllegalArgumentException("未登录，无法发起检测");
        }
        if (sessionInfo.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("当前阶段仅支持老师端检测");
        }
    }

    private String normalizeSubjectScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return "matrix";
        }
        return "matrix";
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

    private String shorten(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= max ? raw : raw.substring(0, max);
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
}
