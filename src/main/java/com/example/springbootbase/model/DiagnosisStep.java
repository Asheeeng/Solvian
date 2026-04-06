package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化步骤信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisStep {
    private Integer stepNo;
    private String title;
    private String content;
    private String latex;
    private Boolean isWrong;
    private String explanation;
}

