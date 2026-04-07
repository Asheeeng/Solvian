package com.example.springbootbase.service;

/**
 * 诊断任务队列服务。
 */
public interface DiagnosisQueueService {
    void enqueue(String taskId);
}
