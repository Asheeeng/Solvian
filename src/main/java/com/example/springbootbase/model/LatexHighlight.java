package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LaTeX 局部高亮信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatexHighlight {
    private String target;
    private String label;
    private String severity;
    private Integer start;
    private Integer end;
}
