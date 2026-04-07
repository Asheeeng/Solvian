package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.entity.DiagnosisTaskEntity;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.service.DiagnosisInputStoreService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 诊断输入图片暂存实现。
 */
@Service
public class DiagnosisInputStoreServiceImpl implements DiagnosisInputStoreService {

    private final Path storageRoot;

    public DiagnosisInputStoreServiceImpl(AiModelProperties aiModelProperties) {
        String configured = aiModelProperties.getTaskStorageDir();
        if (configured == null || configured.isBlank()) {
            this.storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "solvian-diagnosis");
        } else {
            this.storageRoot = Path.of(configured);
        }
    }

    @Override
    public String store(String taskId, PreprocessedImage image) {
        try {
            Files.createDirectories(storageRoot);
            Path target = storageRoot.resolve(taskId + "-" + sanitizeFileName(image.getFileName()));
            Files.write(target, image.getBytes());
            return target.toString();
        } catch (IOException ex) {
            throw new IllegalArgumentException("诊断图片暂存失败: " + ex.getMessage());
        }
    }

    @Override
    public PreprocessedImage load(DiagnosisTaskEntity task) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(task.getInputImagePath()));
            return PreprocessedImage.builder()
                    .fileName(task.getInputImageName())
                    .contentType(task.getInputContentType())
                    .bytes(bytes)
                    .fileSize(bytes.length)
                    .originalFileSize(task.getInputFileSize() == null ? bytes.length : task.getInputFileSize())
                    .imageHash(task.getInputImageHash())
                    .build();
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取诊断图片失败: " + ex.getMessage());
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "diagnosis-input.bin";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
