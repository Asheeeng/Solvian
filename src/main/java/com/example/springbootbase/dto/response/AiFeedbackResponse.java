package com.example.springbootbase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 反馈响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackResponse {
    private boolean success;
    private String message;
    private String feedbackId;
}
