package com.example.springbootbase.service.impl;

import com.example.springbootbase.dto.response.EvaluateResponse;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.mapper.DiagnosisTaskMapper;
import com.example.springbootbase.model.VisionExtractionResult;
import com.example.springbootbase.model.VisionStageResult;
import com.example.springbootbase.service.DiagnosisInputStoreService;
import com.example.springbootbase.worker.ReasoningWorker;
import com.example.springbootbase.worker.VisionWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断任务处理器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisTaskProcessor {

    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisInputStoreService diagnosisInputStoreService;
    private final VisionWorker visionWorker;
    private final ReasoningWorker reasoningWorker;
    private final ObjectMapper objectMapper;

    public void process(String taskId) {
        DiagnosisTaskEntity task = requireTask(taskId);

        try {
            OffsetDateTime now = OffsetDateTime.now();
            task.setStatus("vision");
            task.setProgress(18);
            task.setStageMessage("正在解析题目图像与公式结构");
            task.setStartedAt(now);
            task.setUpdatedAt(now);
            diagnosisTaskMapper.updateById(task);

            com.example.springbootbase.model.PreprocessedImage image = diagnosisInputStoreService.load(task);
            VisionStageResult visionStageResult = visionWorker.process(task, image);
            Map<String, Object> partialResult = buildPartialResult(visionStageResult);

            task.setVisionResultJson(toJson(visionStageResult));
            task.setPartialResultJson(toJson(partialResult));
            task.setStatus("reasoning");
            task.setProgress(58);
            task.setStageMessage(Boolean.TRUE.equals(visionStageResult.getCacheHit())
                    ? "已复用题目识别缓存，正在推理步骤"
                    : "已识别公式与步骤草稿，正在推理错误点");
            task.setUpdatedAt(OffsetDateTime.now());
            diagnosisTaskMapper.updateById(task);

            EvaluateResponse finalResult = reasoningWorker.process(task, image, visionStageResult);
            task.setRecordId(finalResult.getRecordId());
            task.setFinalResultJson(toJson(finalResult));
            task.setStatus("done");
            task.setProgress(100);
            task.setStageMessage("诊断完成，完整结果已生成");
            task.setUpdatedAt(OffsetDateTime.now());
            task.setFinishedAt(OffsetDateTime.now());
            diagnosisTaskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("[diagnosis-task] taskId={} failed", taskId, ex);
            markFailed(taskId, ex.getMessage());
        }
    }

    private DiagnosisTaskEntity requireTask(String taskId) {
        DiagnosisTaskEntity task = diagnosisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("诊断任务不存在");
        }
        return task;
    }

    private void markFailed(String taskId, String errorMessage) {
        DiagnosisTaskEntity task = diagnosisTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("failed");
        task.setProgress(100);
        task.setStageMessage("诊断失败，请稍后重试");
        task.setErrorMessage(errorMessage == null || errorMessage.isBlank() ? "未知错误" : errorMessage);
        task.setUpdatedAt(OffsetDateTime.now());
        task.setFinishedAt(OffsetDateTime.now());
        diagnosisTaskMapper.updateById(task);
    }

    private Map<String, Object> buildPartialResult(VisionStageResult visionStageResult) {
        VisionExtractionResult extraction = visionStageResult.getVisionExtractionResult();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visionModel", visionStageResult.getVisionModel());
        result.put("cacheHit", Boolean.TRUE.equals(visionStageResult.getCacheHit()));
        result.put("problemText", extraction == null ? "" : extraction.getProblemText());
        result.put("studentSteps", extraction == null || extraction.getStudentSteps() == null ? List.of() : extraction.getStudentSteps());
        result.put("matrixExpressions", extraction == null || extraction.getMatrixExpressions() == null ? List.of() : extraction.getMatrixExpressions());
        result.put("imageHighlights", extraction == null || extraction.getImageHighlights() == null ? List.of() : extraction.getImageHighlights());
        result.put("confidence", extraction == null ? null : extraction.getConfidence());
        result.put("summary", extraction == null ? "" : extraction.getRawSummary());
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("任务结果序列化失败", ex);
        }
    }
}
