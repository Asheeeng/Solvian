package com.example.springbootbase.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;

/**
 * AI 配置注入日志（脱敏）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigLogger {

    private final AiModelProperties aiModelProperties;
    private final Environment environment;

    @PostConstruct
    public void logConfig() {
        String host = extractHost(aiModelProperties.getBaseUrl());
        int keyLength = aiModelProperties.getApiKey() == null ? 0 : aiModelProperties.getApiKey().length();

        log.info(
                "[ai-config] profiles={}, enabled={}, provider={}, visionModel={}, reasoningModel={}, baseUrlHost={}, apiKeyMasked={}, apiKeyLength={}, mockEnabled={}, fallbackToMock={}, timeoutMs={}",
                Arrays.toString(environment.getActiveProfiles()),
                aiModelProperties.isEnabled(),
                aiModelProperties.getProvider(),
                aiModelProperties.getVisionModel(),
                aiModelProperties.getModel(),
                host,
                aiModelProperties.maskedApiKey(),
                keyLength,
                aiModelProperties.isMockEnabled(),
                aiModelProperties.isFallbackToMock(),
                aiModelProperties.getTimeoutMs()
        );
    }

    private String extractHost(String baseUrl) {
        try {
            if (baseUrl == null || baseUrl.isBlank()) {
                return "<empty>";
            }
            URI uri = URI.create(baseUrl);
            return uri.getHost() == null ? baseUrl : uri.getHost();
        } catch (Exception ex) {
            return "<invalid>";
        }
    }
}

