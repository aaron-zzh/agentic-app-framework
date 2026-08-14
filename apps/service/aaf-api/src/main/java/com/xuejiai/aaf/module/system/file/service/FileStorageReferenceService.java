package com.xuejiai.aaf.module.system.file.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.storage.LocalStorageService;
import com.xuejiai.aaf.framework.storage.OssStorageService;
import com.xuejiai.aaf.framework.storage.S3StorageService;
import com.xuejiai.aaf.framework.storage.StorageProperties;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;

import lombok.RequiredArgsConstructor;

/** 按文件创建时绑定的存储配置读取对象，并准备 AI 模型可消费的图片引用。 */
@Service
@RequiredArgsConstructor
public class FileStorageReferenceService {

    private static final Duration AI_DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final Map<Long, StorageService> configuredStorages = new ConcurrentHashMap<>();

    private final FileConfigRepository fileConfigRepository;
    private final StorageService defaultStorageService;
    private final StorageProperties storageProperties;

    /**
     * 为 AI 图像模型准备输入：OSS 使用短时签名 URL，本地和 S3 兼容存储转换为 Data URL。
     *
     * @param file 已完成权限校验的文件记录
     * @return 模型可直接使用的图片 URL 或 Data URL
     */
    public String prepareImageInput(FileRecord file) {
        var mimeType = requireImageMimeType(file);
        var storageType = storageType(file);
        var storageService = resolve(file);
        if (storageType == StorageProperties.StorageType.OSS) {
            return storageService.getPresignedDownloadUrl(file.getKey(), AI_DOWNLOAD_URL_EXPIRY);
        }
        try (var input = storageService.download(file.getKey())) {
            return "data:%s;base64,%s"
                    .formatted(mimeType, Base64.getEncoder().encodeToString(input.readAllBytes()));
        } catch (IOException e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "读取图片文件失败");
        }
    }

    /** 按当前主存储配置选择写入目标，并返回必须持久化到文件记录的配置 ID。 */
    public ResolvedStorage resolveCurrentMaster() {
        var config = fileConfigRepository.findByMasterTrue().orElse(null);
        if (config == null) {
            return new ResolvedStorage(null, defaultStorageService);
        }
        return new ResolvedStorage(config.getId(), resolve(config));
    }

    /** 按不可变的存储配置 ID 解析存储服务。 */
    public StorageService resolveByConfigId(Long storageConfigId) {
        if (storageConfigId == null) {
            return defaultStorageService;
        }
        var config =
                fileConfigRepository
                        .findById(storageConfigId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文件绑定的存储配置不存在"));
        return resolve(config);
    }

    /** 按文件绑定的 storageConfigId 解析存储服务；旧文件未绑定配置时才使用当前默认存储。 */
    public StorageService resolve(FileRecord file) {
        return resolveByConfigId(file.getStorageConfigId());
    }

    private StorageService resolve(FileConfig config) {
        if (config.getConfig() == null || config.getConfig().isBlank()) {
            if (storageType(config) == storageProperties.type()) {
                return defaultStorageService;
            }
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件绑定的存储配置缺失");
        }
        return configuredStorages.computeIfAbsent(
                config.getId(), ignored -> createStorageService(config));
    }

    private StorageProperties.StorageType storageType(FileRecord file) {
        if (file.getStorageConfigId() == null) {
            return storageProperties.type();
        }
        var config =
                fileConfigRepository
                        .findById(file.getStorageConfigId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "文件绑定的存储配置不存在"));
        return storageType(config);
    }

    private StorageProperties.StorageType storageType(FileConfig config) {
        try {
            return StorageProperties.StorageType.valueOf(
                    config.getStorageType().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件绑定的存储类型无效");
        }
    }

    private StorageService createStorageService(FileConfig config) {
        try {
            return switch (storageType(config)) {
                case LOCAL ->
                        new LocalStorageService(
                                JsonUtils.parseObject(
                                        config.getConfig(),
                                        StorageProperties.LocalProperties.class));
                case S3 ->
                        new S3StorageService(
                                JsonUtils.parseObject(
                                        config.getConfig(), StorageProperties.S3Properties.class));
                case OSS ->
                        new OssStorageService(
                                JsonUtils.parseObject(
                                        config.getConfig(), StorageProperties.OssProperties.class));
            };
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "文件绑定的存储配置无效");
        }
    }

    /** 存储实例及与其匹配、需写入文件记录的配置 ID。 */
    public record ResolvedStorage(Long storageConfigId, StorageService storageService) {}

    private String requireImageMimeType(FileRecord file) {
        var mimeType = file.getMimeType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "参考文件不是支持的图片类型");
        }
        return mimeType;
    }
}
