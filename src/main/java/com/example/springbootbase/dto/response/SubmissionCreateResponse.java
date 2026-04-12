package com.example.springbootbase.dto.response;

import com.example.springbootbase.vo.SubmissionItemVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作业提交创建响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCreateResponse {
    private boolean success;
    private String message;
    private SubmissionItemVO submission;
}
