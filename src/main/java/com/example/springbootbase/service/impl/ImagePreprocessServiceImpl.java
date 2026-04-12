package com.example.springbootbase.service.impl;

import com.example.springbootbase.config.AiModelProperties;
import com.example.springbootbase.model.PreprocessedImage;
import com.example.springbootbase.service.ImagePreprocessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * 图片预处理实现。
 * 当前阶段优先做模型输入优化，压缩过大的原图并计算缓存哈希。
 */
@Service
@RequiredArgsConstructor
public class ImagePreprocessServiceImpl implements ImagePreprocessService {

    private final AiModelProperties aiModelProperties;

    @Override
    public PreprocessedImage preprocess(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }

        try {
            return preprocess(file.getOriginalFilename(), file.getContentType(), file.getBytes(), file.getSize());
        } catch (IOException e) {
            throw new IllegalArgumentException("图片读取失败");
        }
    }

    @Override
    public PreprocessedImage preprocess(String fileName, String contentType, byte[] bytes) {
        long size = bytes == null ? 0L : bytes.length;
        return preprocess(fileName, contentType, bytes, size);
    }

    private PreprocessedImage preprocess(String fileName, String contentType, byte[] originalBytes, long originalSize) {
        if (originalBytes == null || originalBytes.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }

        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(originalBytes));

            byte[] optimizedBytes = originalBytes;
            String finalContentType = contentType;
            String finalFileName = fileName;
            Integer width = null;
            Integer height = null;

            if (sourceImage != null) {
                width = sourceImage.getWidth();
                height = sourceImage.getHeight();

                if (shouldOptimize(originalSize, width, height)) {
                    optimizedBytes = compressImage(sourceImage);
                    finalContentType = "image/jpeg";
                    finalFileName = normalizeFileName(fileName);
                }
            }

            return PreprocessedImage.builder()
                    .fileName(finalFileName)
                    .contentType(finalContentType)
                    .bytes(optimizedBytes)
                    .fileSize(optimizedBytes.length)
                    .originalFileSize(originalSize)
                    .imageHash(calculateImageHash(originalBytes))
                    .width(width)
                    .height(height)
                    .build();
        } catch (IOException e) {
            throw new IllegalArgumentException("图片读取失败");
        }
    }

    private boolean shouldOptimize(long originalSize, int width, int height) {
        int maxWidth = Math.max(aiModelProperties.getImageMaxWidth(), 512);
        return originalSize > 1024 * 1024L || width > maxWidth || height > maxWidth;
    }

    private byte[] compressImage(BufferedImage sourceImage) throws IOException {
        int maxWidth = Math.max(aiModelProperties.getImageMaxWidth(), 512);
        BufferedImage resizedImage = resizeImageIfNeeded(sourceImage, maxWidth);
        BufferedImage rgbImage = ensureRgbImage(resizedImage);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream fallback = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "png", fallback);
            return fallback.toByteArray();
        }

        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(0.82f);
            }
            writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            writer.dispose();
            return baos.toByteArray();
        }
    }

    private BufferedImage resizeImageIfNeeded(BufferedImage sourceImage, int maxWidth) {
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        if (width <= maxWidth && height <= maxWidth) {
            return sourceImage;
        }

        double scale = Math.min((double) maxWidth / width, (double) maxWidth / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private BufferedImage ensureRgbImage(BufferedImage sourceImage) {
        if (sourceImage.getType() == BufferedImage.TYPE_INT_RGB) {
            return sourceImage;
        }
        BufferedImage rgbImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(sourceImage, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private String normalizeFileName(String fileName) {
        String safeName = fileName == null || fileName.isBlank() ? "diagnosis-input" : fileName;
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex > 0) {
            safeName = safeName.substring(0, dotIndex);
        }
        return safeName + ".jpg";
    }

    private String calculateImageHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法初始化图片哈希算法", e);
        }
    }
}
