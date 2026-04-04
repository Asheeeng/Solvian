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
    private String provider = "mock";
    private String model = "mock-model";
    private String apiKey = "";
    private String baseUrl = "";
}
