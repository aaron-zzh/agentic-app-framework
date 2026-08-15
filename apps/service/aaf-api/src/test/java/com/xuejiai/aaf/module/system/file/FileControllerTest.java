package com.xuejiai.aaf.module.system.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.system.file.config.FileStorageProperties;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;
import com.xuejiai.aaf.module.system.file.service.FileStorageReferenceService;

class FileControllerTest {

    @Test
    @DisplayName("Given 文件记录包含中文原始名称 When 下载文件 Then 响应头返回编码后的原始名称")
    void should_use_encoded_original_name_when_downloading_file() {
        // 准备参数
        var fileStoragePort = mock(FileStoragePort.class);
        var controller = controller(fileStoragePort);
        var file = storedFile("审查报告 最终版.pdf");
        when(fileStoragePort.requireCurrentOwner(1L)).thenReturn(file);
        when(fileStoragePort.openByKey(file.key()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));

        // 调用
        var response = controller.download(1L);

        // 断言
        var header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).isNotBlank();
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(file.originalName());
        verify(fileStoragePort).requireCurrentOwner(1L);
        verify(fileStoragePort).openByKey(file.key());
    }

    @Test
    @DisplayName("Given 文件原始名称为空 When 下载文件 Then 使用存储 key 作为回退名称")
    void should_fallback_to_key_when_original_name_is_blank() {
        // 准备参数
        var fileStoragePort = mock(FileStoragePort.class);
        var controller = controller(fileStoragePort);
        var file = storedFile(" ");
        when(fileStoragePort.requireCurrentOwner(1L)).thenReturn(file);
        when(fileStoragePort.openByKey(file.key()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));

        // 调用
        var response = controller.download(1L);

        // 断言
        var header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(file.key());
    }

    private FileController controller(FileStoragePort fileStoragePort) {
        return new FileController(
                fileStoragePort,
                mock(FileRecordService.class),
                mock(FileStorageReferenceService.class),
                mock(FileStorageProperties.class));
    }

    private StoredFile storedFile(String originalName) {
        return new StoredFile(
                1L,
                "2026/07/31/stored-file.pdf",
                "/api/system/files/1/content",
                originalName,
                "application/pdf",
                1,
                null,
                7L);
    }
}
