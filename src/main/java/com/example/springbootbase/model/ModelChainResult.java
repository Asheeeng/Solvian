package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 两阶段模型调用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelChainResult {
    private String visionModel;
    private String reasoningModel;
    private String visionRaw;
    private String reasoningRaw;
    private VisionExtractionResult visionExtractionResult;
}

