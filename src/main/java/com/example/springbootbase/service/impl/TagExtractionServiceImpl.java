package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.service.TagExtractionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 矩阵标签提取实现。
 */
@Service
public class TagExtractionServiceImpl implements TagExtractionService {

    private static final List<String> ALLOWED_TAGS = List.of(
            "#矩阵加法",
            "#矩阵数乘",
            "#矩阵转置",
            "#矩阵求逆",
            "#矩阵乘法",
            "#矩阵行列式",
            "#矩阵初等变换",
            "#矩阵秩"
    );

    private static final Map<String, String> KEYWORD_TO_TAG = new LinkedHashMap<>();

    static {
        KEYWORD_TO_TAG.put("加法", "#矩阵加法");
        KEYWORD_TO_TAG.put("数乘", "#矩阵数乘");
        KEYWORD_TO_TAG.put("转置", "#矩阵转置");
        KEYWORD_TO_TAG.put("求逆", "#矩阵求逆");
        KEYWORD_TO_TAG.put("逆矩阵", "#矩阵求逆");
        KEYWORD_TO_TAG.put("乘法", "#矩阵乘法");
        KEYWORD_TO_TAG.put("行列式", "#矩阵行列式");
        KEYWORD_TO_TAG.put("初等", "#矩阵初等变换");
        KEYWORD_TO_TAG.put("秩", "#矩阵秩");
    }

    @Override
    public List<String> extractTags(DiagnosisResult diagnosisResult, String rawModelOutput) {
        Set<String> normalized = new LinkedHashSet<>();

        if (diagnosisResult.getTags() != null) {
            for (String tag : diagnosisResult.getTags()) {
                String normalizedTag = normalizeTag(tag);
                if (ALLOWED_TAGS.contains(normalizedTag)) {
                    normalized.add(normalizedTag);
                }
            }
        }

        if (normalized.isEmpty()) {
            String corpus = buildCorpus(diagnosisResult, rawModelOutput);
            for (Map.Entry<String, String> entry : KEYWORD_TO_TAG.entrySet()) {
                if (corpus.contains(entry.getKey())) {
                    normalized.add(entry.getValue());
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add("#矩阵初等变换");
        }

        return new ArrayList<>(normalized);
    }

    private String normalizeTag(String rawTag) {
        if (rawTag == null) {
            return "";
        }
        String trimmed = rawTag.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    private String buildCorpus(DiagnosisResult diagnosisResult, String rawModelOutput) {
        StringBuilder sb = new StringBuilder();
        if (rawModelOutput != null) {
            sb.append(rawModelOutput).append('\n');
        }
        if (diagnosisResult.getFeedback() != null) {
            sb.append(diagnosisResult.getFeedback()).append('\n');
        }
        if (diagnosisResult.getSteps() != null) {
            for (DiagnosisStep step : diagnosisResult.getSteps()) {
                if (step.getTitle() != null) {
                    sb.append(step.getTitle()).append('\n');
                }
                if (step.getContent() != null) {
                    sb.append(step.getContent()).append('\n');
                }
                if (step.getLatex() != null) {
                    sb.append(step.getLatex()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}

