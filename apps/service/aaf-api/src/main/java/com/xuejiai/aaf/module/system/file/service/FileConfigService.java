package com.xuejiai.aaf.module.system.file.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_CONFIG_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_CONFIG_INVALID;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_CONFIG_MASTER_DELETE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.FILE_STORAGE_CONFIG_REFERENCED;

import java.net.URI;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.storage.LocalStorageSpec;
import com.xuejiai.aaf.framework.storage.OssStorageSpec;
import com.xuejiai.aaf.framework.storage.S3StorageSpec;
import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;
import com.xuejiai.aaf.module.system.file.vo.FileConfigCreateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigPageDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigUpdateDTO;
import com.xuejiai.aaf.module.system.file.vo.FileConfigVO;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.node.ObjectNode;

/** 文件存储配置的标准 CRUD 与受控运维命令。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileConfigService
        extends BaseCrudService<
                FileConfig,
                FileConfigVO,
                FileConfigCreateDTO,
                FileConfigUpdateDTO,
                FileConfigPageDTO> {

    private final FileConfigRepository repository;
    private final FileRecordRepository fileRecordRepository;
    private final StorageClientRegistry storageClientRegistry;
    private final StorageRouter storageRouter;
    private final StorageCredentialProvider credentialProvider;
    private final PlatformTransactionManager transactionManager;

    @Override
    protected FileConfigRepository getRepository() {
        return repository;
    }

    @Override
    protected FileConfigVO toVO(FileConfig entity) {
        var storageType = StorageType.valueOf(entity.getStorageType());
        var credentialRef = credentialRef(storageType, entity.getConfig());
        return new FileConfigVO(
                entity.getId(),
                entity.getVersion(),
                entity.getName(),
                entity.getStorageType(),
                safeConfig(entity.getConfig()),
                credentialRef,
                isCredentialConfigured(storageType, credentialRef),
                entity.getMaster(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }

    @Override
    protected FileConfig toEntity(FileConfigCreateDTO request) {
        var storageType = parseStorageType(request.storageType());
        validateStorageConfig(storageType, request.config());
        var entity = new FileConfig();
        entity.setName(request.name());
        entity.setStorageType(storageType.name());
        entity.setConfig(request.config());
        entity.setMaster(false);
        return entity;
    }

    @Override
    protected void beforeUpdate(FileConfig entity, FileConfigUpdateDTO request) {
        var storageType =
                request.storageType() == null
                        ? StorageType.valueOf(entity.getStorageType())
                        : parseStorageType(request.storageType());
        var config = request.config() == null ? entity.getConfig() : request.config();
        if (request.storageType() != null || request.config() != null) {
            validateStorageConfig(storageType, config);
        }
        if (fileRecordRepository.existsByStorageConfigId(entity.getId())) {
            requireReferencedLocationUnchanged(entity, storageType, config);
        }
    }

    @Override
    protected void updateEntity(FileConfig entity, FileConfigUpdateDTO request) {
        var storageType =
                request.storageType() == null
                        ? StorageType.valueOf(entity.getStorageType())
                        : parseStorageType(request.storageType());
        var config = request.config() == null ? entity.getConfig() : request.config();
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.storageType() != null) {
            entity.setStorageType(storageType.name());
        }
        if (request.config() != null) {
            entity.setConfig(config);
        }
        if (request.storageType() != null || request.config() != null) {
            entity.setVersion(entity.getVersion() + 1);
            invalidateAfterCommit(entity.getId());
        }
    }

    @Override
    protected void beforeDelete(FileConfig entity) {
        if (fileRecordRepository.existsByStorageConfigId(entity.getId())) {
            throw exception(FILE_STORAGE_CONFIG_REFERENCED);
        }
        if (Boolean.TRUE.equals(entity.getMaster())) {
            throw exception(FILE_STORAGE_CONFIG_MASTER_DELETE_FORBIDDEN);
        }
    }

    @Override
    protected void afterDelete(FileConfig entity) {
        invalidateAfterCommit(entity.getId());
    }

    /** 真实验证候选配置后，将其设为全局主配置。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileConfigVO setMaster(Long id) {
        var candidate = requireConfig(id);
        validateStorageConfig(
                StorageType.valueOf(candidate.getStorageType()), candidate.getConfig());
        var result =
                new TransactionTemplate(transactionManager).execute(ignored -> switchMaster(id));
        return Objects.requireNonNull(result, "切换主文件存储未返回结果");
    }

    private FileConfigVO switchMaster(Long id) {
        return executeCustomUpdateCommand(
                id,
                id,
                new CustomUpdatePlan<>(
                        "SET_MASTER",
                        java.util.Set.of("master"),
                        (entity, ignored) -> {},
                        (entity, ignored) -> entity.setMaster(true),
                        (entity, ignored) -> null,
                        true,
                        (entity, ignored, result) -> {
                            repository.clearMasterExcept(entity.getId());
                            invalidateMasterAfterCommit();
                        },
                        (entity, ignored, result) -> toVO(entity)));
    }

    /** 通过上传、回读和删除临时对象验证配置真实可用。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void test(Long id) {
        var config = requireConfig(id);
        validateStorageConfig(StorageType.valueOf(config.getStorageType()), config.getConfig());
    }

    private FileConfig requireConfig(Long id) {
        return repository.findById(id).orElseThrow(() -> exception(FILE_CONFIG_NOT_FOUND));
    }

    private void requireReferencedLocationUnchanged(
            FileConfig current, StorageType nextStorageType, String nextConfig) {
        var currentStorageType = StorageType.valueOf(current.getStorageType());
        if (currentStorageType != nextStorageType) {
            throw exception(FILE_STORAGE_CONFIG_REFERENCED);
        }
        switch (currentStorageType) {
            case LOCAL -> {
                var currentSpec =
                        JsonUtils.parseObject(current.getConfig(), LocalStorageSpec.class);
                var nextSpec = JsonUtils.parseObject(nextConfig, LocalStorageSpec.class);
                if (!Objects.equals(currentSpec.basePath(), nextSpec.basePath())) {
                    throw exception(FILE_STORAGE_CONFIG_REFERENCED);
                }
            }
            case S3 -> {
                var currentSpec = JsonUtils.parseObject(current.getConfig(), S3StorageSpec.class);
                var nextSpec = JsonUtils.parseObject(nextConfig, S3StorageSpec.class);
                if (!Objects.equals(currentSpec.endpoint(), nextSpec.endpoint())
                        || !Objects.equals(currentSpec.bucketName(), nextSpec.bucketName())
                        || !Objects.equals(currentSpec.region(), nextSpec.region())) {
                    throw exception(FILE_STORAGE_CONFIG_REFERENCED);
                }
            }
            case OSS -> {
                var currentSpec = JsonUtils.parseObject(current.getConfig(), OssStorageSpec.class);
                var nextSpec = JsonUtils.parseObject(nextConfig, OssStorageSpec.class);
                if (!Objects.equals(currentSpec.endpoint(), nextSpec.endpoint())
                        || !Objects.equals(currentSpec.bucketName(), nextSpec.bucketName())) {
                    throw exception(FILE_STORAGE_CONFIG_REFERENCED);
                }
            }
        }
    }

    private void validateStorageConfig(StorageType storageType, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw exception(FILE_STORAGE_CONFIG_INVALID, "配置内容不能为空");
        }
        try {
            switch (storageType) {
                case LOCAL ->
                        validateLocal(JsonUtils.parseObject(configJson, LocalStorageSpec.class));
                case S3 -> validateS3(JsonUtils.parseObject(configJson, S3StorageSpec.class));
                case OSS -> validateOss(JsonUtils.parseObject(configJson, OssStorageSpec.class));
            }
            storageClientRegistry.validate(storageType, configJson);
        } catch (RuntimeException failure) {
            throw exception(FILE_STORAGE_CONFIG_INVALID, failure.getMessage());
        }
    }

    private void validateLocal(LocalStorageSpec spec) {
        requireText(spec.basePath(), "本地存储配置缺少 basePath");
        validateDomain(spec.domain(), true, "本地存储配置缺少或包含无效 domain");
    }

    private void validateS3(S3StorageSpec spec) {
        requireText(spec.endpoint(), "S3 存储配置缺少 endpoint");
        requireText(spec.bucketName(), "S3 存储配置缺少 bucketName");
        requireText(spec.credentialRef(), "S3 存储配置缺少 credentialRef");
        validateRemoteAccess(
                spec.domain(), spec.enablePublicAccess(), spec.downloadUrlExpirySeconds(), "S3");
    }

    private void validateOss(OssStorageSpec spec) {
        requireText(spec.endpoint(), "OSS 存储配置缺少 endpoint");
        requireText(spec.bucketName(), "OSS 存储配置缺少 bucketName");
        requireText(spec.credentialRef(), "OSS 存储配置缺少 credentialRef");
        validateRemoteAccess(
                spec.domain(), spec.enablePublicAccess(), spec.downloadUrlExpirySeconds(), "OSS");
        if (spec.durationSeconds() != null && spec.durationSeconds() < 900) {
            throw new IllegalArgumentException("OSS 存储配置 durationSeconds 不能小于 900");
        }
    }

    private void validateRemoteAccess(
            String domain, Boolean enablePublicAccess, Integer expirySeconds, String label) {
        if (enablePublicAccess == null) {
            throw new IllegalArgumentException(label + " 存储配置缺少 enablePublicAccess");
        }
        validateDomain(domain, false, label + " 存储配置包含无效 domain");
        if (expirySeconds == null || expirySeconds < 60 || expirySeconds > 86_400) {
            throw new IllegalArgumentException(
                    label + " 存储配置 downloadUrlExpirySeconds 必须在 60 到 86400 之间");
        }
    }

    private void validateDomain(String domain, boolean required, String message) {
        if (domain == null || domain.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(message);
            }
            return;
        }
        try {
            var uri = URI.create(domain);
            if (uri.getHost() == null
                    || (!("http".equalsIgnoreCase(uri.getScheme()))
                            && !("https".equalsIgnoreCase(uri.getScheme())))
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException(message);
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private StorageType parseStorageType(String value) {
        try {
            return StorageType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException failure) {
            throw exception(FILE_STORAGE_CONFIG_INVALID, "不支持的存储类型");
        }
    }

    private String credentialRef(StorageType storageType, String config) {
        return switch (storageType) {
            case LOCAL -> null;
            case S3 -> JsonUtils.parseObject(config, S3StorageSpec.class).credentialRef();
            case OSS -> JsonUtils.parseObject(config, OssStorageSpec.class).credentialRef();
        };
    }

    private boolean isCredentialConfigured(StorageType storageType, String credentialRef) {
        if (storageType == StorageType.LOCAL) {
            return true;
        }
        try {
            credentialProvider.require(credentialRef);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String safeConfig(String config) {
        try {
            var node = JsonUtils.readTree(config);
            if (node instanceof ObjectNode objectNode) {
                objectNode.remove(
                        java.util.List.of(
                                "credentialRef",
                                "accessKey",
                                "accessKeyId",
                                "accessKeySecret",
                                "secret",
                                "secretKey"));
            }
            return JsonUtils.toJsonString(node);
        } catch (RuntimeException ignored) {
            return "{}";
        }
    }

    private void invalidateAfterCommit(Long configId) {
        afterCommit(() -> storageRouter.invalidate(configId));
    }

    private void invalidateMasterAfterCommit() {
        afterCommit(storageRouter::invalidateMaster);
    }

    private void afterCommit(Runnable callback) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            callback.run();
                        }
                    });
            return;
        }
        callback.run();
    }
}
