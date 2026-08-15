package com.xuejiai.aaf.module.system.file.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_CONFIG_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_MASTER_NOT_FOUND;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.enums.FileConfigStatus;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;

import lombok.RequiredArgsConstructor;

/** 按数据库配置路由对象存储，单实例一期不做跨节点缓存一致性。 */
@Service
@RequiredArgsConstructor
public class StorageRouter {

    private final FileConfigRepository fileConfigRepository;
    private final StorageClientRegistry registry;

    public ResolvedStorage currentMaster() {
        var config =
                fileConfigRepository
                        .findByMasterTrueAndStatus(FileConfigStatus.ACTIVE.name())
                        .orElseThrow(() -> exception(FILE_STORAGE_MASTER_NOT_FOUND));
        return resolved(config);
    }

    public ResolvedStorage byConfigId(Long storageConfigId) {
        if (storageConfigId == null) {
            throw new IllegalArgumentException("storageConfigId 不能为空");
        }
        var config =
                fileConfigRepository
                        .findById(storageConfigId)
                        .orElseThrow(() -> exception(FILE_CONFIG_NOT_FOUND));
        return resolved(config);
    }

    private ResolvedStorage resolved(FileConfig config) {
        return new ResolvedStorage(
                config.getId(),
                StorageType.valueOf(config.getStorageType()),
                registry.resolve(config));
    }

    public record ResolvedStorage(
            Long storageConfigId, StorageType storageType, StorageClient client) {}
}
