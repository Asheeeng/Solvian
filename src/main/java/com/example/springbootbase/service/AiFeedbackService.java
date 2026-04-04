package com.example.springbootbase.service;

import com.example.springbootbase.dto.request.AiFeedbackRequest;
import com.example.springbootbase.dto.response.AiFeedbackResponse;
import com.example.springbootbase.model.SessionInfo;

/**
 * AI 识别反馈服务。
 */
public interface AiFeedbackService {
    AiFeedbackResponse submit(AiFeedbackRequest request, SessionInfo sessionInfo);
}
