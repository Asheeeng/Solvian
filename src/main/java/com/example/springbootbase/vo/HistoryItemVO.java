package com.example.springbootbase.vo;

import com.example.springbootbase.auth.AiFeedbackType;
import com.example.springbootbase.auth.ErrorType;
import com.example.springbootbase.auth.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 历史记录输出结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryItemVO {
    private String recordId;
    private String username;
    private Role role;
    private String fileName;
    private String status;
    private Integer errorIndex;
    private List<String> steps;
    private String feedback;
    private Boolean isSocratic;
    private AiFeedbackType aiFeedback;
    private ErrorType errorType;
    private String note;
    private Instant createdAt;
}
