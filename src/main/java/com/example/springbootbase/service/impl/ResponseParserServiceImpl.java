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
        if (root != null) {
            return parseFromJson(root);
        }

        String jsonBlock = extractJsonBlock(rawModelOutput);
        if (jsonBlock != null) {
            JsonNode blockNode = tryParseAsJson(jsonBlock);
            if (blockNode != null) {
                return parseFromJson(blockNode);
            }
        }

        return parseFromText(rawModelOutput);
    }

    private JsonNode tryParseAsJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractJsonBlock(String raw) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private DiagnosisResult parseFromJson(JsonNode root) {
        String status = normalizeStatus(root.path("status").asText(null));
        String feedback = root.path("feedback").asText("诊断完成，但未返回反馈说明。");

        Integer errorIndex = null;
        if (root.has("errorIndex") && !root.get("errorIndex").isNull()) {
            errorIndex = root.get("errorIndex").asInt();
        } else if (root.has("error_index") && !root.get("error_index").isNull()) {
            errorIndex = root.get("error_index").asInt();
        }

        List<DiagnosisStep> steps = parseSteps(root.path("steps"));
        List<String> tags = parseTags(root.path("tags"));
        List<ImageHighlight> imageHighlights = parseImageHighlights(root.path("imageHighlights"));

        String subjectScope = root.path("subjectScope").asText(
                root.path("problemType").asText("matrix")
        );
        Boolean isMatrixProblem = root.has("isMatrixProblem")
                ? root.path("isMatrixProblem").asBoolean(true)
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
        if (!stepsNode.isArray()) {
            return steps;
        }

        int index = 1;
        for (JsonNode item : stepsNode) {
            if (item.isObject()) {
                DiagnosisStep step = DiagnosisStep.builder()
                        .stepNo(item.has("stepNo") ? item.path("stepNo").asInt(index) : index)
                        .title(item.path("title").asText("步骤 " + index))
                        .content(item.path("content").asText(""))
                        .latex(item.path("latex").asText(""))
                        .highlightedLatex(item.path("highlightedLatex").asText(""))
                        .isWrong(item.path("isWrong").asBoolean(false))
                        .explanation(item.path("explanation").asText(""))
                        .latexHighlights(parseLatexHighlights(item.path("latexHighlights")))
                        .matrixCellDiffs(parseMatrixCellDiffs(item.path("matrixCellDiffs")))
                        .build();
                steps.add(step);
            } else if (item.isTextual()) {
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
                    .target(item.path("target").asText(""))
                    .label(item.path("label").asText(""))
                    .severity(item.path("severity").asText("medium"))
                    .start(item.has("start") && !item.get("start").isNull() ? item.path("start").asInt() : null)
                    .end(item.has("end") && !item.get("end").isNull() ? item.path("end").asInt() : null)
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
                    .row(item.has("row") && !item.get("row").isNull() ? item.path("row").asInt() : null)
                    .col(item.has("col") && !item.get("col").isNull() ? item.path("col").asInt() : null)
                    .expected(item.path("expected").asText(""))
                    .actual(item.path("actual").asText(""))
                    .reason(item.path("reason").asText(""))
                    .severity(item.path("severity").asText("medium"))
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
            steps.add(DiagnosisStep.builder()
                    .stepNo(fallbackStepNo)
                    .title("模型原文")
                    .content(raw.length() > 600 ? raw.substring(0, 600) : raw)
                    .latex("")
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
