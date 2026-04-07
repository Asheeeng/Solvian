package com.example.springbootbase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 诊断任务执行器配置。
 */
@Configuration
public class DiagnosisTaskExecutorConfig {

    @Bean(name = "diagnosisTaskExecutor")
    public Executor diagnosisTaskExecutor(AiModelProperties aiModelProperties) {
        int threads = Math.max(aiModelProperties.getTaskWorkerThreads(), 1);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(Math.max(threads, 2));
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("diagnosis-task-");
        executor.initialize();
        return executor;
    }
}
