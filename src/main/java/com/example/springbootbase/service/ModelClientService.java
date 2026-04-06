package com.example.springbootbase.service;

import com.example.springbootbase.model.ModelChainResult;
import com.example.springbootbase.model.PreprocessedImage;

import java.util.Map;

/**
 * 模型调用服务。
 */
public interface ModelClientService {
    ModelChainResult analyze(PreprocessedImage preprocessedImage, boolean isSocratic, String subjectScope);

    Map<String, Object> testTextModel();
}
