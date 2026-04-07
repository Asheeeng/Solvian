package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉模型抽取结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionExtractionResult {
    private String problemText;
    private List<String> studentSteps;
    private List<String> matrixExpressions;
    private List<ImageHighlight> imageHighlights;
    private Boolean isMatrixProblem;
    private Double confidence;
    private String rawSummary;

    public static VisionExtractionResult fallbackFromRaw(String raw) {
        return VisionExtractionResult.builder()
                .problemText(raw == null ? "" : raw)
                .studentSteps(new ArrayList<>())
                .matrixExpressions(new ArrayList<>())
                .imageHighlights(new ArrayList<>())
                .isMatrixProblem(true)
                .confidence(0.3d)
                .rawSummary(raw == null ? "" : raw)
                .build();
    }
}
