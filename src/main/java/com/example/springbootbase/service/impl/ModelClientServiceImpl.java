package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.service.ModelClientService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型调用实现。
 * 当前阶段返回可运行的模拟结果；真实模型调用通过配置开关扩展。
 */
@Service
@RequiredArgsConstructor
public class ModelClientServiceImpl implements ModelClientService {

    private final AiModelProperties aiModelProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String analyze(PreprocessedImage preprocessedImage, boolean isSocratic) {
        // 当前阶段不强依赖真实模型，但保留配置入口，避免硬编码 Key。
        boolean errorFound = preprocessedImage.getFileSize() % 2 != 0;
        String status = errorFound ? "error_found" : "correct";

        List<String> steps = isSocratic
                ? List.of(
                "请先观察题目中的已知条件与目标结论。",
                "逐步检查关键计算环节并定位可能的异常步骤。",
                "给出一条更稳妥的修正思路，供你自行推导验证。")
                : List.of(
                "解析题目图像并抽取关键数学结构。",
                "重构中间步骤并进行一致性校验。",
                "输出最终诊断结论与修正建议。");

        String feedback = errorFound
                ? "检测到疑似错误，建议优先复查第 2 到第 3 步的符号与系数。"
                : "当前步骤整体一致，未发现明显错误。";

        Integer errorIndex = errorFound ? 2 : null;

        Map<String, Object> mathData = new LinkedHashMap<>();
        mathData.put("matrixDetected", true);
        mathData.put("fileSize", preprocessedImage.getFileSize());
        mathData.put("provider", aiModelProperties.getProvider());
        mathData.put("model", aiModelProperties.getModel());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("steps", steps);
        payload.put("feedback", feedback);
        payload.put("errorIndex", errorIndex);
        payload.put("mathData", mathData);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("模型响应生成失败");
        }
    }
}
