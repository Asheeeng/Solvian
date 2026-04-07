package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构化步骤信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisStep {
    private Integer stepNo;
    private String title;
    private String content;
    private String latex;
    private String highlightedLatex;
    private Boolean isWrong;
    private String explanation;
    private List<LatexHighlight> latexHighlights;
    private List<MatrixCellDiff> matrixCellDiffs;
}
