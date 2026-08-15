package com.xuejiai.aaf.module.system.file.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_ACCESS_FAILED;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;

import lombok.RequiredArgsConstructor;

/** 按文件记录的不可变配置 ID 解析底层客户端，并适配 AI 服务输入。 */
@Service
@RequiredArgsConstructor
public class FileStorageReferenceService {

    private static final Duration AI_DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final StorageRouter storageRouter;

    public String prepareImageInput(FileRecord file) {
        var mimeType = requireImageMimeType(file);
        var storage = storageRouter.byConfigId(file.getStorageConfigId());
        if (storage.storageType() == StorageType.OSS) {
            return storage.client().getPresignedDownloadUrl(file.getKey(), AI_DOWNLOAD_URL_EXPIRY);
        }
        try (var input = storage.client().download(file.getKey())) {
            return "data:%s;base64,%s"
                    .formatted(mimeType, Base64.getEncoder().encodeToString(input.readAllBytes()));
        } catch (IOException | RuntimeException failure) {
            throw exception(FILE_STORAGE_ACCESS_FAILED);
        }
    }

    public String prepareExternalAccess(FileRecord file, Duration expiry) {
        var storage = storageRouter.byConfigId(file.getStorageConfigId());
        if (storage.storageType() != StorageType.LOCAL) {
            try {
                return storage.client().getPresignedDownloadUrl(file.getKey(), expiry);
            } catch (RuntimeException failure) {
                throw exception(FILE_STORAGE_ACCESS_FAILED);
            }
        }
        try (var input = storage.client().download(file.getKey())) {
            var mimeType =
                    file.getMimeType() == null || file.getMimeType().isBlank()
                            ? "application/octet-stream"
                            : file.getMimeType();
            return "data:%s;base64,%s"
                    .formatted(mimeType, Base64.getEncoder().encodeToString(input.readAllBytes()));
        } catch (IOException | RuntimeException failure) {
            throw exception(FILE_STORAGE_ACCESS_FAILED);
        }
    }

    public StorageRouter.ResolvedStorage resolveCurrentMaster() {
        return storageRouter.currentMaster();
    }

    public StorageClient resolveByConfigId(Long storageConfigId) {
        return storageRouter.byConfigId(storageConfigId).client();
    }

    public StorageClient resolve(FileRecord file) {
        return resolveByConfigId(file.getStorageConfigId());
    }

    private String requireImageMimeType(FileRecord file) {
        var mimeType = file.getMimeType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new IllegalArgumentException("参考文件不是支持的图片类型");
        }
        return mimeType;
    }
}
