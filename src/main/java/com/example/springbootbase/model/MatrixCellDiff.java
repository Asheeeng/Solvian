package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 矩阵元素差异信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixCellDiff {
    private Integer row;
    private Integer col;
    private String expected;
    private String actual;
    private String reason;
    private String severity;
}
