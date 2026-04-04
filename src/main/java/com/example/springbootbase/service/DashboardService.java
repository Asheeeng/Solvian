package com.example.springbootbase.service;

import com.example.springbootbase.dto.response.DashboardSummaryResponse;
import com.example.springbootbase.model.SessionInfo;

/**
 * 统计服务。
 */
public interface DashboardService {
    DashboardSummaryResponse getSummary(SessionInfo sessionInfo);
}
