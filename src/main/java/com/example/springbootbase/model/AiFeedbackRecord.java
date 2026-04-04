package com.example.springbootbase.model;

import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 反馈记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRecord {
    private String feedbackId;
    private String recordId;
    private String userId;
    private AiFeedbackType aiFeedback;
    private ErrorType errorType;
    private String note;
    private Instant createdAt;
}
