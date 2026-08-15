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

    private static final Duration AI_DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(30);

    private final StorageRouter storageRouter;

    public String prepareImageInput(FileRecord file) {
        var mimeType = requireImageMimeType(file);
        var storage = storageRouter.byConfigId(file.getStorageConfigId());
        if (storage.storageType() != StorageType.LOCAL) {
            return remoteAccessUrl(storage, file.getKey(), AI_DOWNLOAD_URL_EXPIRY);
        }
        return toDataUrl(storage.client(), file.getKey(), mimeType);
    }

    public String prepareExternalAccess(FileRecord file, Duration expiry) {
        var storage = storageRouter.byConfigId(file.getStorageConfigId());
        if (storage.storageType() != StorageType.LOCAL) {
            return remoteAccessUrl(storage, file.getKey(), expiry);
        }
        var mimeType =
                file.getMimeType() == null || file.getMimeType().isBlank()
                        ? "application/octet-stream"
                        : file.getMimeType();
        return toDataUrl(storage.client(), file.getKey(), mimeType);
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

    private String remoteAccessUrl(
            StorageRouter.ResolvedStorage storage, String key, Duration privateExpiry) {
        try {
            return storage.publicAccess()
                    ? storage.client().getUrl(key)
                    : storage.client().getPresignedDownloadUrl(key, privateExpiry);
        } catch (RuntimeException failure) {
            throw exception(FILE_STORAGE_ACCESS_FAILED);
        }
    }

    private String toDataUrl(StorageClient client, String key, String mimeType) {
        try (var input = client.download(key)) {
            return "data:%s;base64,%s"
                    .formatted(mimeType, Base64.getEncoder().encodeToString(input.readAllBytes()));
        } catch (IOException | RuntimeException failure) {
            throw exception(FILE_STORAGE_ACCESS_FAILED);
        }
    }

    private String requireImageMimeType(FileRecord file) {
        var mimeType = file.getMimeType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new IllegalArgumentException("参考文件不是支持的图片类型");
        }
        return mimeType;
    }
}
