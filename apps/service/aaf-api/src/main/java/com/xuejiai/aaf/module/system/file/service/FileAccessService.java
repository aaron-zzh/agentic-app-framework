package com.xuejiai.aaf.module.system.file.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_ACCESS_FAILED;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;

import lombok.RequiredArgsConstructor;

/** 根据文件绑定的动态存储配置生成浏览器访问地址，并提供后端受控读取。 */
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private static final String ACCESS_PATH = "/api/system/files/%d/content";

    private final StorageRouter storageRouter;

    public String accessUrl(FileRecord file) {
        var storage = storageRouter.byConfigId(file.getStorageConfigId());
        try {
            if (storage.storageType() == StorageType.LOCAL) {
                return stripTrailingSlash(storage.domain()) + ACCESS_PATH.formatted(file.getId());
            }
            if (storage.publicAccess()) {
                return storage.client().getUrl(file.getKey());
            }
            return storage.client()
                    .getPresignedDownloadUrl(file.getKey(), storage.downloadUrlExpiry());
        } catch (RuntimeException failure) {
            throw exception(FILE_STORAGE_ACCESS_FAILED);
        }
    }

    public InputStream open(FileRecord file) {
        return storageRouter.byConfigId(file.getStorageConfigId()).client().download(file.getKey());
    }

    private String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
