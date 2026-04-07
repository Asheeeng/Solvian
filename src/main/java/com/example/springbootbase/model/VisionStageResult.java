package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视觉阶段结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionStageResult {
    private String visionModel;
    private String visionRaw;
    private VisionExtractionResult visionExtractionResult;
    private Boolean cacheHit;
}
