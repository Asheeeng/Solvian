package com.example.springbootbase.service;

import com.example.springbootbase.dto.response.HistoryResponse;
import com.example.springbootbase.model.SessionInfo;

/**
 * 历史记录服务。
 */
public interface HistoryService {
    HistoryResponse listHistory(SessionInfo sessionInfo);
}
