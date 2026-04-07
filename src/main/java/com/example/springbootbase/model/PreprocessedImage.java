package com.example.springbootbase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预处理后的图片对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessedImage {
    private String fileName;
    private String contentType;
    private byte[] bytes;
    private long fileSize;
    private long originalFileSize;
    private String imageHash;
    private Integer width;
    private Integer height;
}
