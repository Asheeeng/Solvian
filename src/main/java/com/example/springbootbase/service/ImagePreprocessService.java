package com.example.springbootbase.service;

import com.example.springbootbase.model.PreprocessedImage;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片预处理服务。
 */
public interface ImagePreprocessService {
    PreprocessedImage preprocess(MultipartFile file);

    PreprocessedImage preprocess(String fileName, String contentType, byte[] bytes);
}
