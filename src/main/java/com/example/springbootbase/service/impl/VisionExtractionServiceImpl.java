package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.ImageHighlight;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.service.VisionExtractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视觉结果解析实现。
 */
@Service
public class VisionExtractionServiceImpl implements VisionExtractionService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public VisionExtractionResult parse(String rawVisionOutput) {
        if (rawVisionOutput == null || rawVisionOutput.isBlank()) {
            return VisionExtractionResult.fallbackFromRaw("");
        }

        JsonNode root = parseAsJson(rawVisionOutput);
        if (root == null) {
            String jsonBlock = extractJsonBlock(rawVisionOutput);
            if (jsonBlock != null) {
                root = parseAsJson(jsonBlock);
            }
        }

        if (root == null || !root.isObject()) {
            return VisionExtractionResult.fallbackFromRaw(rawVisionOutput);
        }

        List<String> studentSteps = parseStringArray(root.path("studentSteps"));
        List<String> matrixExpressions = parseStringArray(root.path("matrixExpressions"));
        List<ImageHighlight> imageHighlights = parseImageHighlights(root.path("imageHighlights"));

        return VisionExtractionResult.builder()
                .problemText(root.path("problemText").asText(""))
                .studentSteps(studentSteps)
                .matrixExpressions(matrixExpressions)
                .imageHighlights(imageHighlights)
                .isMatrixProblem(root.path("isMatrixProblem").asBoolean(true))
                .confidence(root.has("confidence") ? root.path("confidence").asDouble(0.5d) : 0.5d)
                .rawSummary(root.path("rawSummary").asText(""))
                .build();
    }

    private JsonNode parseAsJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractJsonBlock(String raw) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    private List<ImageHighlight> parseImageHighlights(JsonNode node) {
        List<ImageHighlight> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }

        node.forEach(item -> {
            if (!item.isObject()) {
                return;
            }
            values.add(ImageHighlight.builder()
                    .x(item.has("x") && !item.get("x").isNull() ? item.path("x").asDouble() : null)
                    .y(item.has("y") && !item.get("y").isNull() ? item.path("y").asDouble() : null)
                    .width(item.has("width") && !item.get("width").isNull() ? item.path("width").asDouble() : null)
                    .height(item.has("height") && !item.get("height").isNull() ? item.path("height").asDouble() : null)
                    .label(item.path("label").asText(""))
                    .stepNo(item.has("stepNo") && !item.get("stepNo").isNull() ? item.path("stepNo").asInt() : null)
                    .severity(item.path("severity").asText("medium"))
                    .coordinateType(item.path("coordinateType").asText("ratio"))
                    .mock(item.has("mock") ? item.path("mock").asBoolean(false) : false)
                    .build());
        });
        return values;
    }
}
