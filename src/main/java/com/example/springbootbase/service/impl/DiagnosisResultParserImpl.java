package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.service.DiagnosisResultParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断结果解析实现。
 */
@Service
public class DiagnosisResultParserImpl implements DiagnosisResultParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DiagnosisResult parse(String rawModelOutput) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) {
            throw new IllegalArgumentException("模型输出为空");
        }

        try {
            JsonNode root = objectMapper.readTree(rawModelOutput);

            String status = root.path("status").asText("error_found");
            String feedback = root.path("feedback").asText("未返回反馈内容");

            Integer errorIndex = null;
            if (root.has("errorIndex") && !root.get("errorIndex").isNull()) {
                errorIndex = root.get("errorIndex").asInt();
            } else if (root.has("error_index") && !root.get("error_index").isNull()) {
                errorIndex = root.get("error_index").asInt();
            }

            List<DiagnosisStep> steps = new ArrayList<>();
            JsonNode stepsNode = root.path("steps");
            if (stepsNode.isArray()) {
                int index = 1;
                for (JsonNode node : stepsNode) {
                    if (node.isObject()) {
                        steps.add(DiagnosisStep.builder()
                                .stepNo(node.path("stepNo").asInt(index))
                                .title(node.path("title").asText("步骤 " + index))
                                .content(node.path("content").asText(""))
                                .latex(node.path("latex").asText(""))
                                .isWrong(node.path("isWrong").asBoolean(false))
                                .explanation(node.path("explanation").asText(""))
                                .build());
                    } else {
                        steps.add(DiagnosisStep.builder()
                                .stepNo(index)
                                .title("步骤 " + index)
                                .content(node.asText())
                                .latex("")
                                .isWrong(false)
                                .explanation("")
                                .build());
                    }
                    index++;
                }
            }
            if (steps.isEmpty()) {
                steps.add(DiagnosisStep.builder()
                        .stepNo(1)
                        .title("步骤 1")
                        .content("未解析到步骤信息")
                        .latex("")
                        .isWrong(false)
                        .explanation("")
                        .build());
            }

            Map<String, Object> mathData = new HashMap<>();
            if (root.has("mathData") && root.get("mathData").isObject()) {
                mathData = objectMapper.convertValue(root.get("mathData"), Map.class);
            }

            return DiagnosisResult.builder()
                    .status(status)
                    .steps(steps)
                    .feedback(feedback)
                    .errorIndex(errorIndex)
                    .tags(new ArrayList<>())
                    .subjectScope("matrix")
                    .isMatrixProblem(true)
                    .mathData(mathData)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("模型输出解析失败");
        }
    }
}
