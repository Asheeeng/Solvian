package com.example.springbootbase.service;

import com.example.springbootbase.model.VisionExtractionResult;

/**
 * 视觉抽取解析服务。
 */
public interface VisionExtractionService {
    VisionExtractionResult parse(String rawVisionOutput);
}

