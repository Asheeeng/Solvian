package com.example.springbootbase.service.impl;

import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.service.ImagePreprocessService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片预处理实现。
 * 当前阶段仅做基础读取，后续可扩展 OpenCV 等预处理。
 */
@Service
public class ImagePreprocessServiceImpl implements ImagePreprocessService {

    @Override
    public PreprocessedImage preprocess(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }

        try {
            return PreprocessedImage.builder()
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .bytes(file.getBytes())
                    .fileSize(file.getSize())
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("图片读取失败");
        }
    }
}
