package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.PreprocessedImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 GLM 客户端（文本与视觉）。
 */
@Service
@RequiredArgsConstructor
public class GlmModelClientService {

    private final AiModelProperties aiModelProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callTextModel(String model, String prompt) {
        requireConfig();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "你是严谨的数学推理助手，请严格返回用户要求的JSON。"),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.2);

        String responseBody = postChatCompletion(requestBody);
        return extractAssistantContent(responseBody);
    }

    public String callVisionModel(String model, PreprocessedImage preprocessedImage, String prompt) {
        requireConfig();
        String imageDataUrl = toDataUrl(preprocessedImage);

        Map<String, Object> userContent = new LinkedHashMap<>();
        userContent.put("role", "user");
        userContent.put("content", List.of(
                Map.of("type", "text", "text", prompt),
                Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
        ));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "你是数学题图像理解助手，请只返回结构化结果。"),
                userContent
        ));
        requestBody.put("temperature", 0.1);

        String responseBody = postChatCompletion(requestBody);
        return extractAssistantContent(responseBody);
    }

    public Map<String, Object> testTextModel(String model, String prompt) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", aiModelProperties.getProvider());
        result.put("baseUrlHost", extractHost(aiModelProperties.getBaseUrl()));
        result.put("apiKeyMasked", aiModelProperties.maskedApiKey());
        result.put("apiKeyLength", aiModelProperties.getApiKey() == null ? 0 : aiModelProperties.getApiKey().length());
        result.put("visionModel", aiModelProperties.getVisionModel());
        result.put("reasoningModel", model);
        result.put("mockEnabled", aiModelProperties.isMockEnabled());

        try {
            requireConfig();
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.1);

            HttpResponse<String> response = postRawChatCompletion(requestBody);
            int statusCode = response.statusCode();
            result.put("httpStatus", statusCode);

            if (HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
                String content = extractAssistantContent(response.body());
                result.put("success", true);
                result.put("message", "真实模型调用成功");
                result.put("responseSnippet", shorten(content, 240));
            } else {
                result.put("success", false);
                result.put("message", "真实模型调用失败");
                result.put("errorDetail", extractErrorMessage(response.body()));
                result.put("rawBodySnippet", shorten(response.body(), 240));
            }
        } catch (Exception ex) {
            result.put("success", false);
            result.put("message", "请求异常");
            result.put("errorDetail", ex.getMessage());
        }

        result.put("latencyMs", System.currentTimeMillis() - start);
        return result;
    }

    private String postChatCompletion(Map<String, Object> requestBody) {
        try {
            HttpResponse<String> response = postRawChatCompletion(requestBody);
            if (!HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
                String detail = extractErrorMessage(response.body());
                throw new IllegalArgumentException("GLM 调用失败，HTTP 状态: " + response.statusCode() + "，详情: " + detail);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("GLM 调用异常: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("GLM 调用异常: " + e.getMessage());
        }
    }

    private HttpResponse<String> postRawChatCompletion(Map<String, Object> requestBody) throws IOException, InterruptedException {
        String endpoint = buildEndpoint(aiModelProperties.getBaseUrl());
        String body = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(Math.max(aiModelProperties.getTimeoutMs(), 3000)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + aiModelProperties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(aiModelProperties.getTimeoutMs(), 3000)))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extractAssistantContent(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");

            if (contentNode.isTextual()) {
                return contentNode.asText();
            }

            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : contentNode) {
                    if (item.has("text")) {
                        sb.append(item.path("text").asText()).append('\n');
                    } else if (item.has("content")) {
                        sb.append(item.path("content").asText()).append('\n');
                    }
                }
                String merged = sb.toString().trim();
                if (!merged.isEmpty()) {
                    return merged;
                }
            }
            throw new IllegalArgumentException("GLM 返回格式不符合预期");
        } catch (IOException ex) {
            throw new IllegalArgumentException("GLM 响应解析异常: " + ex.getMessage());
        }
    }

    private void requireConfig() {
        if (aiModelProperties.getApiKey() == null || aiModelProperties.getApiKey().isBlank()) {
            throw new IllegalArgumentException("未配置 GLM API Key");
        }
        if (aiModelProperties.getBaseUrl() == null || aiModelProperties.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("未配置 GLM baseUrl");
        }
    }

    private String buildEndpoint(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String toDataUrl(PreprocessedImage image) {
        String contentType = image.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = inferContentTypeFromFileName(image.getFileName());
        }
        String base64 = Base64.getEncoder().encodeToString(image.getBytes());
        return "data:" + contentType + ";base64," + base64;
    }

    private String inferContentTypeFromFileName(String fileName) {
        if (fileName == null) {
            return "image/png";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response body";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode err = root.path("error");
            if (!err.isMissingNode()) {
                String code = err.path("code").asText("");
                String msg = err.path("message").asText("");
                String merged = (code + " " + msg).trim();
                if (!merged.isBlank()) {
                    return merged;
                }
            }
            return shorten(responseBody, 200);
        } catch (Exception ignored) {
            return shorten(responseBody, 200);
        }
    }

    private String extractHost(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            return uri.getHost() == null ? baseUrl : uri.getHost();
        } catch (Exception ex) {
            return "<invalid>";
        }
    }

    private String shorten(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= max ? raw : raw.substring(0, max);
    }
}

