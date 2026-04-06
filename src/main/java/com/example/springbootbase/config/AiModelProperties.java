package com.example.springbootbase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置。
 * API Key 从配置或环境变量读取，禁止硬编码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiModelProperties {
    private boolean enabled = false;
    private String provider = "zhipu";
    /**
     * 推理模型（第二阶段）。
     */
    private String model = "glm-4.7";
    /**
     * 视觉模型（第一阶段）。
     */
    private String visionModel = "glm-4.6v";
    private String apiKey = "";
    private String baseUrl = "";
    private boolean mockEnabled = true;
    private boolean fallbackToMock = false;
    private int timeoutMs = 30000;

    public String maskedApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "<empty>";
        }
        if (apiKey.length() <= 10) {
            return "***";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
