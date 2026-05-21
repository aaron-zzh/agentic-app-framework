package com.xuejiai.aaf.framework.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

/**
 * 图片处理工具。
 *
 * <p>基于 Thumbnailator 实现缩略图、水印、压缩、格式转换。
 */
@Slf4j
public class ImageProcessor {

    /** 生成缩略图。 */
    public InputStream thumbnail(InputStream input, int width, int height) {
        try {
            var out = new ByteArrayOutputStream();
            Thumbnails.of(input).size(width, height).toOutputStream(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new StorageException("缩略图生成失败", e);
        }
    }

    /** 添加文字水印。 */
    public InputStream watermark(InputStream input, String text) {
        try {
            // 创建水印图片
            var watermarkImage =
                    new java.awt.image.BufferedImage(
                            200, 50, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = watermarkImage.createGraphics();
            g.setColor(new java.awt.Color(128, 128, 128, 128));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 20));
            g.drawString(text, 10, 35);
            g.dispose();

            var out = new ByteArrayOutputStream();
            Thumbnails.of(input)
                    .scale(1.0)
                    .watermark(Positions.BOTTOM_RIGHT, watermarkImage, 0.5f)
                    .toOutputStream(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new StorageException("水印添加失败", e);
        }
    }

    /**
     * 压缩图片。
     *
     * @param quality 质量 0.0~1.0
     */
    public InputStream compress(InputStream input, float quality) {
        try {
            var out = new ByteArrayOutputStream();
            Thumbnails.of(input).scale(1.0).outputQuality(quality).toOutputStream(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new StorageException("图片压缩失败", e);
        }
    }

    /**
     * 格式转换。
     *
     * @param targetFormat 目标格式（如 "png", "jpg", "webp"）
     */
    public InputStream convert(InputStream input, String targetFormat) {
        try {
            var out = new ByteArrayOutputStream();
            Thumbnails.of(input).scale(1.0).outputFormat(targetFormat).toOutputStream(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new StorageException("图片格式转换失败", e);
        }
    }
}
