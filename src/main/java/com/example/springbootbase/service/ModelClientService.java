package com.example.springbootbase.service;

import com.example.springbootbase.model.PreprocessedImage;

/**
 * 模型调用服务。
 */
public interface ModelClientService {
    String analyze(PreprocessedImage preprocessedImage, boolean isSocratic);
}
