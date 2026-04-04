package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 诊断结构化结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {
    private String status;
    private List<String> steps;
    private String feedback;
    private Integer errorIndex;
    private Map<String, Object> mathData;
}
