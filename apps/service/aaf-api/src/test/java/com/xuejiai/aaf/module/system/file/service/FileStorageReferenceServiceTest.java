package com.xuejiai.aaf.module.system.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.storage.LocalStorageService;
import com.xuejiai.aaf.framework.storage.LocalStorageSpec;
import com.xuejiai.aaf.framework.storage.StorageClient;
import com.xuejiai.aaf.framework.storage.StorageType;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;

class FileStorageReferenceServiceTest {

    @Test
    @DisplayName("Given 文件绑定本地存储配置 When 准备参考图 Then 直接读取对象并返回 Data URL")
    void should_returnDataUrl_when_fileUsesLocalStorageConfig() throws Exception {
        // 准备参数
        var directory = Files.createTempDirectory("aaf-reference-image-");
        Files.write(directory.resolve("image.png"), new byte[] {1, 2, 3});
        var client = new LocalStorageService(new LocalStorageSpec(directory.toString(), "/unused"));
        var router = mock(StorageRouter.class);
        when(router.byConfigId(1L))
                .thenReturn(
                        new StorageRouter.ResolvedStorage(
                                1L,
                                StorageType.LOCAL,
                                "http://localhost:8080",
                                false,
                                Duration.ofHours(1),
                                client));
        var service = new FileStorageReferenceService(router);

        // 调用
        var result = service.prepareImageInput(imageFile(1L, "image.png"));

        // 断言
        assertThat(result).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    @DisplayName("Given 文件绑定 OSS 配置 When 准备参考图 Then 返回短时签名 URL")
    void should_returnPresignedUrl_when_fileUsesOssStorageConfig() {
        // 准备参数
        var client = mock(StorageClient.class);
        when(client.getPresignedDownloadUrl("image.png", Duration.ofMinutes(30)))
                .thenReturn("https://oss.example.com/image.png?signature=temporary");
        var router = mock(StorageRouter.class);
        when(router.byConfigId(2L))
                .thenReturn(
                        new StorageRouter.ResolvedStorage(
                                2L, StorageType.OSS, "", false, Duration.ofHours(1), client));
        var service = new FileStorageReferenceService(router);

        // 调用
        var result = service.prepareImageInput(imageFile(2L, "image.png"));

        // 断言
        assertThat(result).isEqualTo("https://oss.example.com/image.png?signature=temporary");
        verify(client).getPresignedDownloadUrl("image.png", Duration.ofMinutes(30));
    }

    private static FileRecord imageFile(Long storageConfigId, String key) {
        var file = new FileRecord();
        file.setStorageConfigId(storageConfigId);
        file.setKey(key);
        file.setMimeType("image/png");
        return file;
    }
}
