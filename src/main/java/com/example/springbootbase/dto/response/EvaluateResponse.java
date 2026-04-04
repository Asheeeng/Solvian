package com.example.springbootbase.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI 诊断响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateResponse {
    private String recordId;
    private String status;
    private List<String> steps;
    private String feedback;
    private Integer errorIndex;
    private Map<String, Object> mathData;
    private Boolean isSocratic;

    /**
     * 兼容旧版字段命名。
     */
    @JsonProperty("error_index")
    public Integer getErrorIndexSnakeCase() {
        return errorIndex;
    }
}
