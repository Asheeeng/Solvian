package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.model.PreprocessedImage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 模型调用实现。
 */
@Service
public class MockModelClientService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String analyze(PreprocessedImage preprocessedImage, boolean isSocratic, String subjectScope, String fallbackReason) {
        long fileSize = preprocessedImage.getFileSize();

        String status;
        Integer errorIndex;
        List<DiagnosisStep> steps = new ArrayList<>();

        if (fileSize <= 0) {
            status = "unable_to_judge";
            errorIndex = null;
            steps.add(DiagnosisStep.builder()
                    .stepNo(1)
                    .title("图像信息不足")
                    .content("未获取到有效题目信息，无法判断完整解题逻辑。")
                    .latex("\\\\text{Insufficient image data}")
                    .isWrong(false)
                    .explanation("")
                    .build());
        } else if (fileSize % 2 == 0) {
            status = "correct";
            errorIndex = null;
            steps.add(DiagnosisStep.builder()
                    .stepNo(1)
                    .title("矩阵条件识别")
                    .content("识别到题目为矩阵运算，先确认维度匹配。")
                    .latex("A\\in\\mathbb{R}^{m\\times n},\\ B\\in\\mathbb{R}^{m\\times n}")
                    .isWrong(false)
                    .explanation("")
                    .build());
            steps.add(DiagnosisStep.builder()
                    .stepNo(2)
                    .title("执行运算")
                    .content("按对应元素进行矩阵运算并得到中间结果。")
                    .latex("C=A+B,\\ c_{ij}=a_{ij}+b_{ij}")
                    .isWrong(false)
                    .explanation("")
                    .build());
            steps.add(DiagnosisStep.builder()
                    .stepNo(3)
                    .title("结果复核")
                    .content("复核关键元素与维度一致性，结论正确。")
                    .latex("\\text{Check dimensions and entries}")
                    .isWrong(false)
                    .explanation("")
                    .build());
        } else {
            status = "error_found";
            errorIndex = 2;
            steps.add(DiagnosisStep.builder()
                    .stepNo(1)
                    .title("矩阵条件识别")
                    .content("已识别为矩阵运算题，维度检查通过。")
                    .latex("A\\in\\mathbb{R}^{m\\times n},\\ B\\in\\mathbb{R}^{m\\times n}")
                    .isWrong(false)
                    .explanation("")
                    .build());
            steps.add(DiagnosisStep.builder()
                    .stepNo(2)
                    .title("核心运算")
                    .content("在第二步将一项符号写反，导致后续结果偏差。")
                    .latex("c_{12}=a_{12}-b_{12}\\quad(\\text{应为 }a_{12}+b_{12})")
                    .isWrong(true)
                    .explanation("第 2 步把加法误写为减法，符号错误导致最终答案错误。")
                    .build());
            steps.add(DiagnosisStep.builder()
                    .stepNo(3)
                    .title("结果复核")
                    .content("因为前一步符号错误，最终矩阵结果不正确。")
                    .latex("\\text{Final matrix is inconsistent with constraints}")
                    .isWrong(false)
                    .explanation("")
                    .build());
        }

        List<String> tags = List.of("#矩阵加法", "#矩阵数乘");
        if (isSocratic) {
            tags = List.of("#矩阵初等变换", "#矩阵秩");
        }

        Map<String, Object> mathData = new LinkedHashMap<>();
        mathData.put("provider", "mock");
        mathData.put("source", "mock-fallback");
        mathData.put("fileSize", fileSize);
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            mathData.put("fallbackReason", fallbackReason);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("subjectScope", subjectScope == null || subjectScope.isBlank() ? "matrix" : subjectScope);
        payload.put("isMatrixProblem", true);
        payload.put("steps", steps);
        payload.put("errorIndex", errorIndex);
        payload.put("feedback", status.equals("correct")
                ? "当前矩阵解题过程逻辑完整，未发现明显错误。"
                : status.equals("error_found")
                ? "已定位到关键错误步骤，请优先修正后续计算。"
                : "图像信息不足，当前无法稳定判断。");
        payload.put("tags", tags);
        payload.put("mathData", mathData);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("mock 结果序列化失败");
        }
    }
}
