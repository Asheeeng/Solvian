package com.example.springbootbase.service;

import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.model.PreprocessedImage;

/**
 * 诊断输入图片暂存服务。
 */
public interface DiagnosisInputStoreService {
    String store(String taskId, PreprocessedImage image);

    PreprocessedImage load(DiagnosisTaskEntity task);
}
