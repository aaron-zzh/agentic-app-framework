package com.xuejiai.aaf.framework.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地文件系统存储实现。
 *
 * <p>文件按日期分目录存储：{basePath}/{yyyy/MM/dd}/{uuid}.{ext}
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties.LocalProperties config;

    public LocalStorageService(StorageProperties.LocalProperties config) {
        this.config = config;
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        var ext = extractExtension(filename);
        var key = LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + ext;
        var target = Path.of(config.basePath(), key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
        } catch (IOException e) {
            throw new StorageException("本地文件上传失败", e);
        }
        log.info("本地存储上传: {}", key);
        return key;
    }

    @Override
    public InputStream download(String key) {
        try {
            return Files.newInputStream(Path.of(config.basePath(), key));
        } catch (IOException e) {
            throw new StorageException("本地文件下载失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(Path.of(config.basePath(), key));
        } catch (IOException e) {
            throw new StorageException("本地文件删除失败: " + key, e);
        }
    }

    @Override
    public String getUrl(String key) {
        return config.urlPrefix() + "/" + key;
    }

    @Override
    public String getPresignedUploadUrl(String key, Duration expiry) {
        throw new UnsupportedOperationException("本地存储不支持预签名上传");
    }

    private String extractExtension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
