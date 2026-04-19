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
                当前关注矩阵加法、减法、乘法、数乘、转置、初等行变换等常见矩阵题（subjectScope=matrix）。
                你必须做“字面抄写”，不能做“数学纠错”或“自动修正”。

                请只返回严格 JSON：
                {
                  "problemText": "题目核心信息，尽量简短",
                  "studentSteps": ["R3 -> -1R3", "3A", "A+B"],
                  "matrixExpressions": [
                    "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}",
                    "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}",
                    "\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}"
                  ],
                  "imageHighlights": [],
                  "isMatrixProblem": true,
                  "confidence": 0.0,
                  "rawSummary": "一句话摘要"
                }

                识别要求：
                1) 如果图片里出现“原矩阵 + 行变换操作 + 结果矩阵”，matrixExpressions 放两个矩阵，顺序必须是 [原矩阵, 结果矩阵]。
                2) 如果图片里是矩阵加减法、乘法或数乘，请按从左到右保留关键矩阵表达式，最后一个尽量是学生写出的结果矩阵。
                3) studentSteps 里优先提取运算规则或操作文本，例如“R3 -> -1R3”“A+B”“3A”“AB”；没有就返回空数组。
                4) 逐格核对矩阵元素，特别注意负号、分数、0/6/8、1/7、以及未参与变换的行列。
                5) 绝对禁止因为“数学上应该如此”而把学生写错的数字改成正确数字。图上写错也要原样抄下来。
                6) 如果操作只作用于某一行，但图上的其他行也被学生改错了，仍然要把这些错误数字原样写进结果矩阵，不能私自纠正成原矩阵。
                7) 如果某个数字看不清，优先保留视觉上最像的写法并降低 confidence；不要为了让矩阵“更合理”而替换数字。
                8) 不要把解释文字混进 matrixExpressions，matrixExpressions 里只放 LaTeX 矩阵表达式。
                9) imageHighlights 无法稳定定位时返回空数组。
                10) confidence 范围 0~1。

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
    public String buildStrictMatrixAuditPrompt(VisionExtractionResult firstPassResult) {
        return """
                你正在做“矩阵题字面复核”，只允许逐字抄写图片上的内容，禁止任何数学修正。
                任务目标：重新抄写图片中的原矩阵、操作文本、结果矩阵。
                重点：即使图片里的结果矩阵明显算错，也必须原样返回错误数字。

                首轮识别结果（仅供你定位区域，不代表正确）：
                - problemText: %s
                - studentSteps: %s
                - matrixExpressions: %s

                请只返回严格 JSON：
                {
                  "problemText": "题目核心信息，尽量简短",
                  "studentSteps": ["R1 -> 2R1"],
                  "matrixExpressions": [
                    "\\\\begin{bmatrix}...\\\\end{bmatrix}",
                    "\\\\begin{bmatrix}...\\\\end{bmatrix}"
                  ],
                  "imageHighlights": [],
                  "isMatrixProblem": true,
                  "confidence": 0.0,
                  "rawSummary": "一句话说明这次是逐字复核结果"
                }

                复核要求：
                1) 只做逐字抄写，不做运算，不做脑补。
                2) 如果图中结果矩阵某一行明显不该变但学生写变了，仍然必须照抄错误后的那一行。
                3) matrixExpressions 对于行变换题必须只返回两个矩阵：[原矩阵, 学生写出的结果矩阵]。
                4) 如果你认为首轮识别比图片更“合理”，也不能沿用首轮识别，必须以图片字面内容为准。
                5) 如果实在看不清，请降低 confidence，不要偷偷修正。
                """.formatted(
                normalizeText(firstPassResult == null ? "" : firstPassResult.getProblemText(), 300),
                summarizeList(firstPassResult == null ? List.of() : firstPassResult.getStudentSteps(), 6, 180),
                summarizeList(firstPassResult == null ? List.of() : firstPassResult.getMatrixExpressions(), 4, 240)
        );
    }

    @Override
    public String buildReasoningPrompt(VisionExtractionResult visionExtractionResult, boolean isSocratic, String subjectScope) {
        String modeText = isSocratic
                ? "用简短启发式口吻。"
                : "用简短教师批改口吻。";

        return """
                你是矩阵题批改助手。请基于已识别内容直接给出结构化结论。
                当前处理矩阵加减法、乘法、数乘、转置、初等行变换等矩阵题（subjectScope=matrix）。
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

                判定要求：
                1) 如果题图是矩阵加减法、乘法或数乘，请直接按矩阵运算法则核对学生结果。
                2) 如果题图是行变换，请重点检查受影响的行，同时确认未参与变换的行保持不变。
                3) 对行变换题，只要发现未参与变换的行被学生改动，也必须判定为 error_found，不能因为“可能是 OCR 偏差”就输出 correct。
                4) 只有在确实看不清、信息缺失或 OCR 不稳定时才返回 unable_to_judge。
                5) 如果返回 correct，steps 中所有 isWrong 必须为 false，matrixCellDiffs 必须为空。

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
