package com.example.springbootbase.model;

import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 诊断记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisRecord {
    private String recordId;
    private String userId;
    private String username;
    private Role role;

    private String fileName;
    private Boolean isSocratic;

    private String status;
    private List<String> steps;
    private String feedback;
    private Integer errorIndex;
    private Map<String, Object> mathData;

    private AiFeedbackType aiFeedback;
    private ErrorType errorType;
    private String note;

    private Instant createdAt;
}
