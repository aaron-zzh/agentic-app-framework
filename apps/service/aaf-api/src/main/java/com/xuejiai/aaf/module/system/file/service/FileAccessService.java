package com.xuejiai.aaf.module.system.file.service;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.system.file.domain.FileRecord;

import lombok.RequiredArgsConstructor;

/** 浏览器文件访问唯一入口，禁止向浏览器暴露对象存储直链。 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private static final String ACCESS_PATH = "/api/system/files/%d/content";

    private final StorageRouter storageRouter;

    public String accessUrl(Long fileId) {
        return ACCESS_PATH.formatted(fileId);
    }

    public InputStream open(FileRecord file) {
        return storageRouter.byConfigId(file.getStorageConfigId()).client().download(file.getKey());
    }
}
