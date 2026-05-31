package com.xuejiai.aaf.module.system.license.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** 官方源码包下载服务。 */
@Service
public class SourceArchiveService {

    private final String archivePath;

    public SourceArchiveService(@Value("${aaf.license.source-archive-path:}") String archivePath) {
        this.archivePath = archivePath;
    }

    public Resource load() {
        if (archivePath == null || archivePath.isBlank()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "未配置源码包路径");
        }
        var path = Path.of(archivePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "源码包不存在");
        }
        return new FileSystemResource(path);
    }

    public String filename() {
        if (archivePath == null || archivePath.isBlank()) {
            return "aaf-source.zip";
        }
        var name = Path.of(archivePath).getFileName();
        return name == null ? "aaf-source.zip" : name.toString();
    }
}
