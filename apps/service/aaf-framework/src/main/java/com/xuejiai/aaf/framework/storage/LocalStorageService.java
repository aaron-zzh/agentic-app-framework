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

/** 本地文件系统存储客户端。 */
@Slf4j
public class LocalStorageService implements StorageClient {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final LocalStorageSpec config;

    public LocalStorageService(LocalStorageSpec config) {
        this.config = config;
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        UploadPolicy.assertNotActiveContent(filename, contentType);
        var ext = extractExtension(filename);
        var key = LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + ext;
        var target = resolveSafe(key);
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
            return Files.newInputStream(resolveSafe(key));
        } catch (IOException e) {
            throw new StorageException("本地文件下载失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafe(key));
        } catch (IOException e) {
            throw new StorageException("本地文件删除失败: " + key, e);
        }
    }

    private Path resolveSafe(String key) {
        var base = Path.of(config.basePath()).toAbsolutePath().normalize();
        var target = base.resolve(key).normalize();
        if (!target.startsWith(base)) {
            throw new StorageException("非法路径: " + key, null);
        }
        return target;
    }

    @Override
    public String getUrl(String key) {
        var domain = config.domain();
        if (domain == null || domain.isBlank()) {
            throw new StorageException("本地存储缺少后端访问 domain", null);
        }
        return domain.endsWith("/") ? domain + key : domain + "/" + key;
    }

    @Override
    public PresignedUploadTicket getPresignedUploadUrl(PresignedUploadRequest request) {
        throw new UnsupportedOperationException("本地存储不支持预签名上传");
    }

    @Override
    public String getPresignedDownloadUrl(String key, Duration expiry) {
        return getUrl(key);
    }

    private String extractExtension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
