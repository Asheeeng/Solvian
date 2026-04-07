package com.example.springbootbase.service.impl;

import com.example.springbootbase.service.DiagnosisQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * 诊断任务队列实现。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisQueueServiceImpl implements DiagnosisQueueService {

    @Qualifier("diagnosisTaskExecutor")
    private final Executor diagnosisTaskExecutor;
    private final DiagnosisTaskProcessor diagnosisTaskProcessor;

    @Override
    public void enqueue(String taskId) {
        diagnosisTaskExecutor.execute(() -> diagnosisTaskProcessor.process(taskId));
    }
}
