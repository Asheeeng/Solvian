package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原图高亮框信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageHighlight {
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private String label;
    private Integer stepNo;
    private String severity;
    private String coordinateType;
    private Boolean mock;
}
