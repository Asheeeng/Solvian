package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.entity.SubmissionEntity;
import com.example.springbootbase.service.SubmissionStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 学生提交文件存储实现。
 */
@Service
public class SubmissionStorageServiceImpl implements SubmissionStorageService {

    private final Path storageRoot;

    public SubmissionStorageServiceImpl(AiModelProperties aiModelProperties) {
        String configured = aiModelProperties.getTaskStorageDir();
        if (configured == null || configured.isBlank()) {
            this.storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "solvian-submissions");
        } else {
            this.storageRoot = Path.of(configured).resolve("submissions");
        }
    }

    @Override
    public String store(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要提交的作业图片");
        }
        try {
            Files.createDirectories(storageRoot);
            String safeName = sanitizeFileName(fileName);
            Path target = storageRoot.resolve(Instant.now().toEpochMilli() + "-" + safeName);
            Files.write(target, file.getBytes());
            return target.toString();
        } catch (IOException ex) {
            throw new IllegalArgumentException("作业图片保存失败: " + ex.getMessage());
        }
    }

    @Override
    public byte[] readBytes(SubmissionEntity submission) {
        try {
            return Files.readAllBytes(Path.of(submission.getImagePath()));
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取作业图片失败: " + ex.getMessage());
        }
    }

    @Override
    public Resource loadAsResource(SubmissionEntity submission) {
        Path path = Path.of(submission.getImagePath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("作业图片不存在");
        }
        return new FileSystemResource(path);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "submission-image.bin";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
