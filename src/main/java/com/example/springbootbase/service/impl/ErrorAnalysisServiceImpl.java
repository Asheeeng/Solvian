package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.service.ErrorAnalysisService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 错误定位与结果修正实现。
 */
@Service
public class ErrorAnalysisServiceImpl implements ErrorAnalysisService {

    @Override
    public DiagnosisResult analyze(DiagnosisResult diagnosisResult, String rawModelOutput) {
        if (diagnosisResult == null) {
            return buildUnableToJudge();
        }

        String status = normalizeStatus(diagnosisResult.getStatus());
        diagnosisResult.setStatus(status);
        diagnosisResult.setSubjectScope(
                diagnosisResult.getSubjectScope() == null || diagnosisResult.getSubjectScope().isBlank()
                        ? "matrix"
                        : diagnosisResult.getSubjectScope()
        );

        List<DiagnosisStep> steps = diagnosisResult.getSteps() == null
                ? new ArrayList<>()
                : new ArrayList<>(diagnosisResult.getSteps());

        if (steps.isEmpty()) {
            steps.add(DiagnosisStep.builder()
                    .stepNo(1)
                    .title("结果解析占位")
                    .content("模型未返回结构化步骤，已使用兜底结果。")
                    .latex("")
                    .isWrong(false)
                    .explanation("")
                    .build());
            status = "unable_to_judge";
            diagnosisResult.setStatus(status);
        }

        for (int i = 0; i < steps.size(); i++) {
            DiagnosisStep step = steps.get(i);
            if (step.getStepNo() == null || step.getStepNo() <= 0) {
                step.setStepNo(i + 1);
            }
            if (step.getTitle() == null || step.getTitle().isBlank()) {
                step.setTitle("步骤 " + step.getStepNo());
            }
            if (step.getContent() == null) {
                step.setContent("");
            }
            if (step.getLatex() == null) {
                step.setLatex("");
            }
            if (step.getHighlightedLatex() == null) {
                step.setHighlightedLatex("");
            }
            if (step.getIsWrong() == null) {
                step.setIsWrong(false);
            }
            if (step.getExplanation() == null) {
                step.setExplanation("");
            }
            if (step.getLatexHighlights() == null) {
                step.setLatexHighlights(new ArrayList<>());
            }
            if (step.getMatrixCellDiffs() == null) {
                step.setMatrixCellDiffs(new ArrayList<>());
            }
        }

        Integer errorIndex = diagnosisResult.getErrorIndex();

        if ("correct".equals(status)) {
            for (DiagnosisStep step : steps) {
                step.setIsWrong(false);
                if (step.getExplanation() == null) {
                    step.setExplanation("");
                }
                step.setHighlightedLatex("");
                step.setLatexHighlights(new ArrayList<>());
                step.setMatrixCellDiffs(new ArrayList<>());
            }
            errorIndex = null;
        } else if ("error_found".equals(status)) {
            if (errorIndex == null || errorIndex <= 0 || errorIndex > steps.size()) {
                errorIndex = findWrongStep(steps);
            }
            if (errorIndex == null) {
                errorIndex = 1;
            }

            int finalIndex = errorIndex;
            for (DiagnosisStep step : steps) {
                boolean isCurrentWrong = step.getStepNo() != null && step.getStepNo() == finalIndex;
                if (isCurrentWrong) {
                    step.setIsWrong(true);
                    if (step.getExplanation() == null || step.getExplanation().isBlank()) {
                        step.setExplanation("该步骤存在逻辑或计算错误，请重点复核。");
                    }
                    if ((step.getHighlightedLatex() == null || step.getHighlightedLatex().isBlank())
                            && step.getLatex() != null && !step.getLatex().isBlank()) {
                        step.setHighlightedLatex(step.getLatex());
                    }
                }
            }
        } else {
            errorIndex = null;
        }

        diagnosisResult.setErrorIndex(errorIndex);
        diagnosisResult.setSteps(steps);

        if (diagnosisResult.getFeedback() == null || diagnosisResult.getFeedback().isBlank()) {
            diagnosisResult.setFeedback(defaultFeedback(status));
        }
        if (diagnosisResult.getImageHighlights() == null) {
            diagnosisResult.setImageHighlights(new ArrayList<>());
        }
        if (diagnosisResult.getDiffInfo() == null) {
            diagnosisResult.setDiffInfo(new LinkedHashMap<>());
        }

        return diagnosisResult;
    }

    private Integer findWrongStep(List<DiagnosisStep> steps) {
        for (DiagnosisStep step : steps) {
            if (Boolean.TRUE.equals(step.getIsWrong())) {
                return step.getStepNo();
            }
        }
        return null;
    }

    private String defaultFeedback(String status) {
        return switch (status) {
            case "correct" -> "步骤整体正确，未发现明显逻辑错误。";
            case "error_found" -> "发现可定位的步骤错误，请根据提示修正。";
            default -> "当前无法稳定判断，请提供更清晰题目图片。";
        };
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unable_to_judge";
        }
        return switch (raw.trim().toLowerCase()) {
            case "correct" -> "correct";
            case "error_found" -> "error_found";
            default -> "unable_to_judge";
        };
    }

    private DiagnosisResult buildUnableToJudge() {
        List<DiagnosisStep> steps = List.of(
                DiagnosisStep.builder()
                        .stepNo(1)
                        .title("结果不可用")
                        .content("暂时无法从模型响应中提取有效步骤。")
                        .latex("")
                        .highlightedLatex("")
                        .isWrong(false)
                        .explanation("")
                        .latexHighlights(new ArrayList<>())
                        .matrixCellDiffs(new ArrayList<>())
                        .build()
        );

        return DiagnosisResult.builder()
                .status("unable_to_judge")
                .steps(steps)
                .feedback("暂时无法稳定判断，请稍后重试。")
                .errorIndex(null)
                .subjectScope("matrix")
                .isMatrixProblem(true)
                .tags(new ArrayList<>())
                .imageHighlights(new ArrayList<>())
                .diffInfo(new LinkedHashMap<>())
                .build();
    }
}
