package com.example.springbootbase.dto.request;

import lombok.Data;

/**
 * AI 识别反馈请求。
 */
@Data
public class AiFeedbackRequest {
    private String recordId;
    private String aiFeedback;
    private String errorType;
    private String note;
}
