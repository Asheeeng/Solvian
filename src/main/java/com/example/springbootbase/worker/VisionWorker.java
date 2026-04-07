package com.example.springbootbase.worker;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.mapper.DiagnosisTaskMapper;
import com.example.springbootbase.model.VisionStageResult;
import com.example.springbootbase.service.ModelClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 视觉识别 worker。
 */
@Component
@RequiredArgsConstructor
public class VisionWorker {

    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final ModelClientService modelClientService;
    private final ObjectMapper objectMapper;

    public VisionStageResult process(DiagnosisTaskEntity task, com.example.springbootbase.model.PreprocessedImage image) {
        VisionStageResult cachedResult = findReusableVisionResult(task.getInputImageHash());
        if (cachedResult != null) {
            cachedResult.setCacheHit(true);
            return cachedResult;
        }

        VisionStageResult freshResult = modelClientService.analyzeVision(image, task.getSubjectScope());
        freshResult.setCacheHit(false);
        return freshResult;
    }

    private VisionStageResult findReusableVisionResult(String imageHash) {
        if (imageHash == null || imageHash.isBlank()) {
            return null;
        }

        QueryWrapper<DiagnosisTaskEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("input_image_hash", imageHash)
                .isNotNull("vision_result_json")
                .in("status", "reasoning", "done", "failed")
                .orderByDesc("updated_at")
                .last("LIMIT 1");

        DiagnosisTaskEntity cachedTask = diagnosisTaskMapper.selectOne(wrapper);
        if (cachedTask == null || cachedTask.getVisionResultJson() == null || cachedTask.getVisionResultJson().isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(cachedTask.getVisionResultJson(), VisionStageResult.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
