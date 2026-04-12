package com.example.springbootbase.service;

import com.example.springbootbase.entity.SubmissionEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提交文件存储服务。
 */
public interface SubmissionStorageService {
    String store(MultipartFile file, String fileName);

    byte[] readBytes(SubmissionEntity submission);

    Resource loadAsResource(SubmissionEntity submission);
}
