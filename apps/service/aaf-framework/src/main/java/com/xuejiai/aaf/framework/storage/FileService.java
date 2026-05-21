package com.xuejiai.aaf.framework.storage;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件服务门面。
 *
 * <p>在 StorageService 之上提供业务级文件操作，包括图片自动缩略图。
 */
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private final StorageService storageService;
    private final ImageProcessor imageProcessor;

    /** 上传文件。 */
    public FileVO upload(MultipartFile file) {
        try {
            var key =
                    storageService.upload(
                            file.getInputStream(),
                            file.getOriginalFilename(),
                            file.getContentType());
            var url = storageService.getUrl(key);
            return new FileVO(
                    key, url, file.getOriginalFilename(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new StorageException("文件上传失败", e);
        }
    }

    /**
     * 上传图片（自动生成缩略图）。
     *
     * <p>缩略图 key 为 {原始key}_thumb.{ext}
     */
    public FileVO uploadImage(MultipartFile file) {
        var result = upload(file);
        // 生成缩略图
        try {
            var thumbStream = imageProcessor.thumbnail(file.getInputStream(), 200, 200);
            var thumbKey = buildThumbKey(result.key());
            storageService.upload(thumbStream, thumbKey, file.getContentType());
            log.info("缩略图已生成: {}", thumbKey);
        } catch (IOException e) {
            log.warn("缩略图生成失败，主文件已上传: {}", result.key(), e);
        }
        return result;
    }

    /** 删除文件。 */
    public void delete(String key) {
        storageService.delete(key);
        // 尝试删除缩略图（忽略不存在的情况）
        try {
            storageService.delete(buildThumbKey(key));
        } catch (Exception ignored) {
            // 缩略图可能不存在
        }
    }

    /** 获取文件访问 URL。 */
    public String getUrl(String key) {
        return storageService.getUrl(key);
    }

    private String buildThumbKey(String key) {
        var dot = key.lastIndexOf('.');
        if (dot >= 0) {
            return key.substring(0, dot) + "_thumb" + key.substring(dot);
        }
        return key + "_thumb";
    }
}
