package com.xuejiai.aaf.module.system.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.storage.StorageProperties;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.system.file.domain.FileConfig;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.repository.FileConfigRepository;

class FileStorageReferenceServiceTest {

    @Test
    @DisplayName("Given 文件绑定本地存储配置 When 准备参考图 Then 直接读取对象并返回 Data URL")
    void should_returnDataUrl_when_fileUsesLocalStorageConfig() throws Exception {
        // 准备参数
        var directory = Files.createTempDirectory("aaf-reference-image-");
        Files.write(directory.resolve("image.png"), new byte[] {1, 2, 3});
        var config = new FileConfig();
        config.setId(1L);
        config.setStorageType("LOCAL");
        config.setConfig(
                JsonUtils.toJsonString(
                        new StorageProperties.LocalProperties(directory.toString(), "/files")));
        var file = imageFile(1L, "image.png");
        var configRepository = mock(FileConfigRepository.class);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        var defaultStorage = mock(StorageService.class);
        var service =
                new FileStorageReferenceService(
                        configRepository,
                        defaultStorage,
                        new StorageProperties(
                                StorageProperties.StorageType.OSS, null, null, null, null));

        // 调用
        var result = service.prepareImageInput(file);

        // 断言
        assertThat(result).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    @DisplayName("Given 文件绑定 OSS 配置 When 准备参考图 Then 返回短时签名 URL")
    void should_returnPresignedUrl_when_fileUsesOssStorageConfig() {
        // 准备参数
        var config = new FileConfig();
        config.setId(2L);
        config.setStorageType("OSS");
        var file = imageFile(2L, "image.png");
        var configRepository = mock(FileConfigRepository.class);
        when(configRepository.findById(2L)).thenReturn(Optional.of(config));
        var defaultStorage = mock(StorageService.class);
        when(defaultStorage.getPresignedDownloadUrl("image.png", Duration.ofMinutes(5)))
                .thenReturn("https://oss.example.com/image.png?signature=temporary");
        var service =
                new FileStorageReferenceService(
                        configRepository,
                        defaultStorage,
                        new StorageProperties(
                                StorageProperties.StorageType.OSS, null, null, null, null));

        // 调用
        var result = service.prepareImageInput(file);

        // 断言
        assertThat(result).isEqualTo("https://oss.example.com/image.png?signature=temporary");
        verify(defaultStorage).getPresignedDownloadUrl("image.png", Duration.ofMinutes(5));
    }

    private static FileRecord imageFile(Long storageConfigId, String key) {
        var file = new FileRecord();
        file.setStorageConfigId(storageConfigId);
        file.setKey(key);
        file.setMimeType("image/png");
        return file;
    }
}
