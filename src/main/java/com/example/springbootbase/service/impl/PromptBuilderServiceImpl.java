package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.service.PromptBuilderService;
import org.springframework.stereotype.Service;

/**
 * 两阶段提示词构建实现。
 */
@Service
public class PromptBuilderServiceImpl implements PromptBuilderService {

    @Override
    public String buildVisionPrompt(PreprocessedImage preprocessedImage, String subjectScope) {
        return """
                你是数学题视觉理解助手。请从题目图片中抽取题面信息。
                当前只关注矩阵类题目（subjectScope=matrix）。

                请输出严格 JSON，不要附加任何解释文字：
                {
                  "problemText": "题目原文或核心信息",
                  "studentSteps": ["学生作答步骤1","学生作答步骤2"],
                  "matrixExpressions": ["\\\u005c\u005cbegin{bmatrix}...\\\u005c\u005cend{bmatrix}"],
                  "imageHighlights": [
                    {
                      "x": 0.58,
                      "y": 0.34,
                      "width": 0.24,
                      "height": 0.16,
                      "label": "第2步相关区域",
                      "stepNo": 2,
                      "severity": "high",
                      "coordinateType": "ratio"
                    }
                  ],
                  "isMatrixProblem": true,
                  "confidence": 0.0,
                  "rawSummary": "视觉阶段摘要"
                }

                额外要求：
                1) 尽量保留矩阵表达式。
                2) studentSteps 没有也要返回空数组。
                3) confidence 范围 0~1。
                4) imageHighlights 若无法定位请返回空数组。
                5) imageHighlights 的坐标统一使用相对比例（0~1），基于原始图片宽高。

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
                ? "采用启发式讲解风格，引导老师发现学生错误。"
                : "采用标准教师批改风格，直接给出严谨结论。";

        return """
                你是数学老师端 AI 推理助手。请基于视觉阶段抽取结果进行逻辑分析。
                当前只处理矩阵题（subjectScope=matrix）。

                视觉抽取结果：
                - problemText: %s
                - studentSteps: %s
                - matrixExpressions: %s
                - imageHighlights: %s
                - isMatrixProblem: %s
                - confidence: %s
                - rawSummary: %s

                推理要求：
                1) 判断解题状态 status：correct / error_found / unable_to_judge。
                2) 输出详细步骤 steps（数组），每步必须有：
                   stepNo, title, content, latex, highlightedLatex, isWrong, explanation。
                3) 若公式里能明确定位错误，请在 highlightedLatex 中直接把错误片段用红色标记，例如 \\color{red}{...}。
                4) 若能定位矩阵元素差异，请在 matrixCellDiffs 中输出 row, col, expected, actual, reason, severity。
                5) 若能明确指出公式局部错误，可在 latexHighlights 中输出 target, label, severity, start, end。
                6) 若有错误，给出 errorIndex（错误步骤编号）。
                7) 输出 feedback（总体评价）。
                8) 输出 tags（字符串数组，标签以 # 开头）。
                9) 输出 imageHighlights（数组），用于前端直接在原图上框出疑似错误区域；坐标继续使用 0~1 相对比例。
                10) 输出 diffInfo（对象），用于概括“正确结果 vs 学生结果”的主要差异。
                11) 步骤中的 latex / highlightedLatex 字段尽量给可渲染公式。

                可选标签：
                #矩阵加法 #矩阵数乘 #矩阵转置 #矩阵求逆 #矩阵乘法 #矩阵行列式 #矩阵初等变换 #矩阵秩

                输出必须是严格 JSON，不要 markdown，不要额外解释：
                {
                  "status": "correct|error_found|unable_to_judge",
                  "steps": [
                    {
                      "stepNo": 1,
                      "title": "步骤标题",
                      "content": "自然语言说明",
                      "latex": "\\\u005c\u005ctext{公式}",
                      "highlightedLatex": "\\\u005c\u005ctext{带局部高亮的公式}",
                      "isWrong": false,
                      "explanation": "错误原因或空字符串",
                      "latexHighlights": [
                        {
                          "target": "a_{12}-b_{12}",
                          "label": "符号错误",
                          "severity": "high",
                          "start": null,
                          "end": null
                        }
                      ],
                      "matrixCellDiffs": [
                        {
                          "row": 1,
                          "col": 2,
                          "expected": "a_{12}+b_{12}",
                          "actual": "a_{12}-b_{12}",
                          "reason": "把加法误写成减法",
                          "severity": "high"
                        }
                      ]
                    }
                  ],
                  "errorIndex": null,
                  "feedback": "总体反馈",
                  "tags": ["#矩阵加法"],
                  "imageHighlights": [
                    {
                      "x": 0.58,
                      "y": 0.34,
                      "width": 0.24,
                      "height": 0.16,
                      "label": "错误步骤 2",
                      "stepNo": 2,
                      "severity": "high",
                      "coordinateType": "ratio"
                    }
                  ],
                  "diffInfo": {
                    "summary": "第2步将加号误写成减号",
                    "type": "operator_mismatch"
                  },
                  "subjectScope": "matrix",
                  "isMatrixProblem": true
                }

                分析风格：%s
                """.formatted(
                safe(visionExtractionResult.getProblemText()),
                safe(visionExtractionResult.getStudentSteps()),
                safe(visionExtractionResult.getMatrixExpressions()),
                safe(visionExtractionResult.getImageHighlights()),
                String.valueOf(visionExtractionResult.getIsMatrixProblem()),
                String.valueOf(visionExtractionResult.getConfidence()),
                safe(visionExtractionResult.getRawSummary()),
                modeText
        );
    }

    private String safe(Object raw) {
        return raw == null ? "" : String.valueOf(raw);
    }
}
