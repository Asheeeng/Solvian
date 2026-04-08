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

        if (root == null) {
            root = extractEmbeddedObject(rawVisionOutput);
        }

        if (root == null || !root.isObject()) {
            return VisionExtractionResult.fallbackFromRaw(rawVisionOutput);
        }

        List<String> studentSteps = parseStringArray(resolveNode(root, "studentSteps", "student_steps"));
        List<String> matrixExpressions = parseStringArray(resolveNode(root, "matrixExpressions", "matrix_expressions", "formulas"));
        List<ImageHighlight> imageHighlights = parseImageHighlights(resolveNode(root, "imageHighlights", "image_highlights", "boxes", "boundingBoxes"));

        return VisionExtractionResult.builder()
                .problemText(readText(root, "", "problemText", "problem_text", "questionText"))
                .studentSteps(studentSteps)
                .matrixExpressions(matrixExpressions)
                .imageHighlights(imageHighlights)
                .isMatrixProblem(readBoolean(root, true, "isMatrixProblem", "is_matrix_problem"))
                .confidence(root.has("confidence") ? root.path("confidence").asDouble(0.5d) : 0.5d)
                .rawSummary(readText(root, "", "rawSummary", "raw_summary", "summary"))
                .build();
    }

    private JsonNode parseAsJson(String text) {
        try {
            JsonNode parsed = objectMapper.readTree(text);
            return unwrapTextualJsonNode(parsed, 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode unwrapTextualJsonNode(JsonNode node, int depth) {
        if (node == null || depth > 3) {
            return node;
        }
        if (!node.isTextual()) {
            return node;
        }
        try {
            JsonNode reparsed = objectMapper.readTree(node.asText());
            return unwrapTextualJsonNode(reparsed, depth + 1);
        } catch (Exception ignored) {
            return node;
        }
    }

    private String extractJsonBlock(String raw) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private JsonNode extractEmbeddedObject(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return parseAsJson(raw.substring(start, end + 1));
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isTextual()) {
            JsonNode reparsed = parseAsJson(node.asText());
            if (reparsed != null) {
                node = reparsed;
            }
        }
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
                    .x(readDouble(item, "x", "left"))
                    .y(readDouble(item, "y", "top"))
                    .width(readDouble(item, "width", "w"))
                    .height(readDouble(item, "height", "h"))
                    .label(readText(item, "", "label", "title"))
                    .stepNo(readNullableInteger(item, "stepNo", "step_no"))
                    .severity(readText(item, "medium", "severity", "level"))
                    .coordinateType(readText(item, "ratio", "coordinateType", "coordinate_type"))
                    .mock(readBoolean(item, false, "mock"))
                    .build());
        });
        return values;
    }

    private JsonNode resolveNode(JsonNode root, String... fieldNames) {
        if (root == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (root.has(fieldName)) {
                return root.get(fieldName);
            }
        }
        return null;
    }

    private String readText(JsonNode node, String fallback, String... fieldNames) {
        if (node == null) {
            return fallback;
        }
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                String value = node.get(fieldName).asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return fallback;
    }

    private boolean readBoolean(JsonNode node, boolean fallback, String... fieldNames) {
        if (node == null) {
            return fallback;
        }
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asBoolean();
            }
        }
        return fallback;
    }

    private Double readDouble(JsonNode node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asDouble();
            }
        }
        return null;
    }

    private Integer readNullableInteger(JsonNode node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asInt();
            }
        }
        return null;
    }
}
