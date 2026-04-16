package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.ImageHighlight;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.service.PromptBuilderService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 两阶段提示词构建实现。
 */
@Service
public class PromptBuilderServiceImpl implements PromptBuilderService {

    @Override
    public String buildVisionPrompt(PreprocessedImage preprocessedImage, String subjectScope) {
        return """
                你是矩阵题视觉识别助手，只做图片识别，不做长篇解释。
                当前只关注矩阵初等变换 / 行变换类题目（subjectScope=matrix）。

                请只返回严格 JSON：
                {
                  "problemText": "题目核心信息，尽量简短",
                  "studentSteps": ["R3 -> -1R3"],
                  "matrixExpressions": [
                    "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}",
                    "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}"
                  ],
                  "imageHighlights": [],
                  "isMatrixProblem": true,
                  "confidence": 0.0,
                  "rawSummary": "一句话摘要"
                }

                识别要求：
                1) 如果图片里出现“原矩阵 + 行变换操作 + 结果矩阵”，matrixExpressions 只放两个矩阵，顺序必须是 [原矩阵, 结果矩阵]。
                2) studentSteps 里优先放“R3 -> -1R3”这类操作文本；没有就返回空数组。
                3) 逐格核对矩阵元素，特别注意负号、1/7、3/8、分数、以及未参与变换的行。
                4) 如果操作只作用于某一行，则其他行在结果矩阵里必须与原矩阵完全一致；看不清时不要猜，直接降低 confidence。
                5) 不要把解释文字混进 matrixExpressions。
                6) imageHighlights 无法稳定定位时返回空数组。
                7) confidence 范围 0~1。

                图片元信息：
                - fileName: %s
                - fileSize: %d
                - subjectScope: %s
                """.formatted(
                safe(preprocessedImage.getFileName()),
                preprocessedImage.getFileSize(),
                safe(subjectScope)
        );
    }

    @Override
    public String buildReasoningPrompt(VisionExtractionResult visionExtractionResult, boolean isSocratic, String subjectScope) {
        String modeText = isSocratic
                ? "用简短启发式口吻。"
                : "用简短教师批改口吻。";

        return """
                你是矩阵题批改助手。请基于已识别内容直接给出结构化结论。
                当前只处理矩阵题（subjectScope=matrix）。
                不要输出 markdown，不要输出思考过程，不要输出额外字段。

                已识别信息：
                - problemText: %s
                - studentSteps: %s
                - matrixExpressions: %s
                - imageHighlights: %s
                - isMatrixProblem: %s
                - confidence: %s
                - rawSummary: %s

                输出严格 JSON：
                {
                  "status": "correct|error_found|unable_to_judge",
                  "steps": [
                    {
                      "stepNo": 1,
                      "title": "步骤标题",
                      "content": "简短说明",
                      "latex": "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}",
                      "highlightedLatex": "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}",
                      "isWrong": false,
                      "explanation": "原因说明",
                      "latexHighlights": [],
                      "matrixCellDiffs": []
                    }
                  ],
                  "errorIndex": null,
                  "feedback": "总体反馈",
                  "tags": ["#矩阵初等变换"],
                  "imageHighlights": [],
                  "diffInfo": {
                    "summary": "一句话概括主要差异",
                    "type": "operator_mismatch|matrix_cell_mismatch|ocr_uncertain"
                  },
                  "subjectScope": "matrix",
                  "isMatrixProblem": true
                }

                分析风格：%s
                """.formatted(
                normalizeText(visionExtractionResult.getProblemText(), 600),
                summarizeList(visionExtractionResult.getStudentSteps(), 8, 220),
                summarizeList(visionExtractionResult.getMatrixExpressions(), 8, 220),
                summarizeHighlights(visionExtractionResult.getImageHighlights(), 6),
                String.valueOf(visionExtractionResult.getIsMatrixProblem()),
                String.valueOf(visionExtractionResult.getConfidence()),
                normalizeText(visionExtractionResult.getRawSummary(), 320),
                modeText
        );
    }

    private String safe(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }

    private String normalizeText(String raw, int maxLength) {
        String text = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String summarizeList(List<?> items, int maxItems, int maxItemLength) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(items.size(), maxItems);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(i + 1)
                    .append(". ")
                    .append(normalizeText(String.valueOf(items.get(i)), maxItemLength));
        }
        if (items.size() > limit) {
            builder.append("; ... 共 ").append(items.size()).append(" 项");
        }
        builder.append(']');
        return builder.toString();
    }

    private String summarizeHighlights(List<ImageHighlight> highlights, int maxItems) {
        if (highlights == null || highlights.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(highlights.size(), maxItems);
        for (int i = 0; i < limit; i++) {
            ImageHighlight item = highlights.get(i);
            if (i > 0) {
                builder.append("; ");
            }
            builder.append("step=")
                    .append(item.getStepNo() == null ? "-" : item.getStepNo())
                    .append(", label=")
                    .append(normalizeText(item.getLabel(), 40))
                    .append(", box=(")
                    .append(item.getX())
                    .append(',')
                    .append(item.getY())
                    .append(',')
                    .append(item.getWidth())
                    .append(',')
                    .append(item.getHeight())
                    .append(')');
        }
        if (highlights.size() > limit) {
            builder.append("; ... 共 ").append(highlights.size()).append(" 项");
        }
        builder.append(']');
        return builder.toString();
    }
}
