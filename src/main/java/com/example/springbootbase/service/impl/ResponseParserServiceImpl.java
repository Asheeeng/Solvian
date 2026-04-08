package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.DiagnosisResult;
import com.example.springbootbase.model.DiagnosisStep;
import com.example.springbootbase.model.ImageHighlight;
import com.example.springbootbase.model.LatexHighlight;
import com.example.springbootbase.model.MatrixCellDiff;
import com.example.springbootbase.service.ResponseParserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型响应解析实现。
 */
@Service
public class ResponseParserServiceImpl implements ResponseParserService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
    private static final Pattern INDEX_STEP_PATTERN = Pattern.compile("^(\\d+)[\\.、\\)]\\s*(.*)$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DiagnosisResult parse(String rawModelOutput) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) {
            return fallbackResult("unable_to_judge", "模型无返回内容，暂时无法判断。");
        }

        JsonNode root = tryParseAsJson(rawModelOutput);
        if (root != null && root.isObject()) {
            return parseFromJson(root);
        }

        String jsonBlock = extractJsonBlock(rawModelOutput);
        if (jsonBlock != null) {
            JsonNode blockNode = tryParseAsJson(jsonBlock);
            if (blockNode != null && blockNode.isObject()) {
                return parseFromJson(blockNode);
            }
        }

        JsonNode embeddedNode = extractEmbeddedObject(rawModelOutput);
        if (embeddedNode != null && embeddedNode.isObject()) {
            return parseFromJson(embeddedNode);
        }

        return parseFromText(rawModelOutput);
    }

    private JsonNode tryParseAsJson(String text) {
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
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return node;
        }
        try {
            JsonNode reparsed = objectMapper.readTree(value);
            return unwrapTextualJsonNode(reparsed, depth + 1);
        } catch (Exception ignored) {
            return node;
        }
    }

    private String extractJsonBlock(String raw) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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
        return tryParseAsJson(raw.substring(start, end + 1));
    }

    private DiagnosisResult parseFromJson(JsonNode root) {
        String status = normalizeStatus(readText(root, null, "status", "resultStatus"));
        String feedback = readText(root, "诊断完成，但未返回反馈说明。", "feedback", "summary", "message");

        Integer errorIndex = null;
        if (root.has("errorIndex") && !root.get("errorIndex").isNull()) {
            errorIndex = root.get("errorIndex").asInt();
        } else if (root.has("error_index") && !root.get("error_index").isNull()) {
            errorIndex = root.get("error_index").asInt();
        }

        JsonNode stepsNode = root.has("steps")
                ? root.get("steps")
                : (root.has("step_list") ? root.get("step_list") : root.path("steps"));
        if (stepsNode != null && stepsNode.isTextual()) {
            JsonNode reparsedSteps = tryParseAsJson(stepsNode.asText());
            if (reparsedSteps != null) {
                stepsNode = reparsedSteps;
            }
        }
        List<DiagnosisStep> steps = parseSteps(stepsNode);
        List<String> tags = parseTags(root.path("tags"));
        List<ImageHighlight> imageHighlights = parseImageHighlights(resolveArrayNode(root, "imageHighlights", "image_highlights", "boxes", "boundingBoxes"));

        String subjectScope = readText(root, readText(root, "matrix", "problemType", "problem_type"), "subjectScope", "subject_scope");
        Boolean isMatrixProblem = root.has("isMatrixProblem") || root.has("is_matrix_problem")
                ? readBoolean(root, true, "isMatrixProblem", "is_matrix_problem")
                : "matrix".equalsIgnoreCase(subjectScope);

        Map<String, Object> mathData = new HashMap<>();
        if (root.has("mathData") && root.get("mathData").isObject()) {
            mathData = objectMapper.convertValue(root.get("mathData"), Map.class);
        }

        Map<String, Object> diffInfo = new HashMap<>();
        if (root.has("diffInfo") && root.get("diffInfo").isObject()) {
            diffInfo = objectMapper.convertValue(root.get("diffInfo"), Map.class);
        }

        return DiagnosisResult.builder()
                .status(status)
                .steps(steps)
                .feedback(feedback)
                .errorIndex(errorIndex)
                .tags(tags)
                .imageHighlights(imageHighlights)
                .subjectScope(subjectScope)
                .isMatrixProblem(isMatrixProblem)
                .diffInfo(diffInfo)
                .mathData(mathData)
                .build();
    }

    private List<DiagnosisStep> parseSteps(JsonNode stepsNode) {
        List<DiagnosisStep> steps = new ArrayList<>();
        if (stepsNode == null) {
            return steps;
        }
        if (stepsNode.isTextual()) {
            JsonNode reparsed = tryParseAsJson(stepsNode.asText());
            if (reparsed != null) {
                stepsNode = reparsed;
            }
        }
        if (!stepsNode.isArray()) {
            return steps;
        }

        int index = 1;
        for (JsonNode item : stepsNode) {
            if (item.isObject()) {
                DiagnosisStep step = DiagnosisStep.builder()
                        .stepNo(readInteger(item, index, "stepNo", "step_no", "step"))
                        .title(readText(item, "步骤 " + index, "title", "name"))
                        .content(readText(item, "", "content", "description", "detail"))
                        .latex(readText(item, "", "latex", "tex", "formula"))
                        .highlightedLatex(readText(item, "", "highlightedLatex", "highlighted_latex", "highlightLatex"))
                        .isWrong(readBoolean(item, false, "isWrong", "is_wrong", "wrong"))
                        .explanation(readText(item, "", "explanation", "errorMessage", "error_message", "reason"))
                        .latexHighlights(parseLatexHighlights(resolveArrayNode(item, "latexHighlights", "latex_highlights")))
                        .matrixCellDiffs(parseMatrixCellDiffs(resolveArrayNode(item, "matrixCellDiffs", "matrix_cell_diffs")))
                        .build();
                steps.add(step);
            } else if (item.isTextual()) {
                JsonNode nestedNode = tryParseAsJson(item.asText());
                if (nestedNode != null && nestedNode.isObject()) {
                    steps.addAll(parseSteps(objectNodeToSingleItemArray(nestedNode)));
                } else if (nestedNode != null && nestedNode.isArray()) {
                    steps.addAll(parseSteps(nestedNode));
                } else {
                    steps.add(DiagnosisStep.builder()
                            .stepNo(index)
                            .title("步骤 " + index)
                            .content(item.asText())
                            .latex("")
                            .highlightedLatex("")
                            .isWrong(false)
                            .explanation("")
                            .latexHighlights(new ArrayList<>())
                            .matrixCellDiffs(new ArrayList<>())
                            .build());
                }
            }
            index++;
        }
        return steps;
    }

    private List<String> parseTags(JsonNode tagsNode) {
        List<String> tags = new ArrayList<>();
        if (tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                if (tag.isTextual()) {
                    tags.add(tag.asText());
                }
            }
        }
        return tags;
    }

    private List<ImageHighlight> parseImageHighlights(JsonNode node) {
        List<ImageHighlight> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
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
        }
        return values;
    }

    private List<LatexHighlight> parseLatexHighlights(JsonNode node) {
        List<LatexHighlight> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            values.add(LatexHighlight.builder()
                    .target(readText(item, "", "target", "text", "actual"))
                    .label(readText(item, "", "label", "reason"))
                    .severity(readText(item, "medium", "severity", "level"))
                    .start(readNullableInteger(item, "start", "startIndex", "start_index"))
                    .end(readNullableInteger(item, "end", "endIndex", "end_index"))
                    .build());
        }
        return values;
    }

    private List<MatrixCellDiff> parseMatrixCellDiffs(JsonNode node) {
        List<MatrixCellDiff> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }

        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            values.add(MatrixCellDiff.builder()
                    .row(readNullableInteger(item, "row", "rowIndex", "row_index"))
                    .col(readNullableInteger(item, "col", "column", "colIndex", "col_index"))
                    .expected(readText(item, "", "expected", "correct"))
                    .actual(readText(item, "", "actual", "student"))
                    .reason(readText(item, "", "reason", "label"))
                    .severity(readText(item, "medium", "severity", "level"))
                    .build());
        }
        return values;
    }

    private DiagnosisResult parseFromText(String raw) {
        String[] lines = raw.split("\\r?\\n");
        List<DiagnosisStep> steps = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        String status = "unable_to_judge";
        String feedback = "模型返回非结构化文本，已执行兜底解析。";
        Integer errorIndex = null;

        int fallbackStepNo = 1;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String lower = trimmed.toLowerCase();
            if (lower.contains("error_found")) {
                status = "error_found";
            } else if (lower.contains("correct")) {
                status = "correct";
            } else if (lower.contains("unable_to_judge")) {
                status = "unable_to_judge";
            }

            if (trimmed.startsWith("反馈") || lower.startsWith("feedback")) {
                feedback = trimmed;
            }

            Matcher matcher = INDEX_STEP_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                int stepNo = Integer.parseInt(matcher.group(1));
                steps.add(DiagnosisStep.builder()
                        .stepNo(stepNo)
                        .title("步骤 " + stepNo)
                        .content(matcher.group(2))
                        .latex("")
                        .highlightedLatex("")
                        .isWrong(false)
                        .explanation("")
                        .latexHighlights(new ArrayList<>())
                        .matrixCellDiffs(new ArrayList<>())
                        .build());
                fallbackStepNo = Math.max(fallbackStepNo, stepNo + 1);
            }

            if (trimmed.startsWith("#")) {
                tags.add(trimmed.split("\\s+")[0]);
            }
        }

        if (steps.isEmpty()) {
            String extractedLatex = extractLatexFromText(raw);
            steps.add(DiagnosisStep.builder()
                    .stepNo(fallbackStepNo)
                    .title("模型原文")
                    .content(extractedLatex.isBlank() ? (raw.length() > 600 ? raw.substring(0, 600) : raw) : "")
                    .latex(extractedLatex)
                    .highlightedLatex("")
                    .isWrong(false)
                    .explanation("")
                    .latexHighlights(new ArrayList<>())
                    .matrixCellDiffs(new ArrayList<>())
                    .build());
        }

        return DiagnosisResult.builder()
                .status(status)
                .steps(steps)
                .feedback(feedback)
                .errorIndex(errorIndex)
                .tags(tags)
                .imageHighlights(new ArrayList<>())
                .subjectScope("matrix")
                .isMatrixProblem(true)
                .diffInfo(new HashMap<>())
                .mathData(new HashMap<>())
                .build();
    }

    private JsonNode resolveArrayNode(JsonNode node, String... fieldNames) {
        if (node == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode resolved = node.get(fieldName);
                if (resolved != null && !resolved.isNull()) {
                    if (resolved.isTextual()) {
                        JsonNode reparsed = tryParseAsJson(resolved.asText());
                        if (reparsed != null) {
                            return reparsed;
                        }
                    }
                    return resolved;
                }
            }
        }
        return null;
    }

    private JsonNode objectNodeToSingleItemArray(JsonNode node) {
        return objectMapper.createArrayNode().add(node);
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

    private Integer readInteger(JsonNode node, Integer fallback, String... fieldNames) {
        Integer resolved = readNullableInteger(node, fieldNames);
        return resolved == null ? fallback : resolved;
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

    private String extractLatexFromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher blockMatcher = Pattern.compile("(\\\\begin\\{[a-zA-Z*]+\\}[\\s\\S]+?\\\\end\\{[a-zA-Z*]+\\})").matcher(raw);
        if (blockMatcher.find()) {
            return blockMatcher.group(1).trim();
        }
        Matcher displayMatcher = Pattern.compile("\\\\\\[([\\s\\S]+?)\\\\\\]").matcher(raw);
        if (displayMatcher.find()) {
            return displayMatcher.group(1).trim();
        }
        Matcher dollarsMatcher = Pattern.compile("\\$\\$([\\s\\S]+?)\\$\\$").matcher(raw);
        if (dollarsMatcher.find()) {
            return dollarsMatcher.group(1).trim();
        }
        return "";
    }

    private DiagnosisResult fallbackResult(String status, String feedback) {
        List<DiagnosisStep> steps = List.of(
                DiagnosisStep.builder()
                        .stepNo(1)
                        .title("结果不可用")
                        .content("当前没有可解析的模型结果。")
                        .latex("")
                        .highlightedLatex("")
                        .isWrong(false)
                        .explanation("")
                        .latexHighlights(new ArrayList<>())
                        .matrixCellDiffs(new ArrayList<>())
                        .build()
        );

        return DiagnosisResult.builder()
                .status(normalizeStatus(status))
                .steps(steps)
                .feedback(feedback)
                .errorIndex(null)
                .tags(new ArrayList<>())
                .imageHighlights(new ArrayList<>())
                .subjectScope("matrix")
                .isMatrixProblem(true)
                .diffInfo(new HashMap<>())
                .mathData(new HashMap<>())
                .build();
    }

    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unable_to_judge";
        }
        return switch (raw.trim().toLowerCase()) {
            case "correct" -> "correct";
            case "error_found" -> "error_found";
            case "unable_to_judge" -> "unable_to_judge";
            default -> "unable_to_judge";
        };
    }
}
