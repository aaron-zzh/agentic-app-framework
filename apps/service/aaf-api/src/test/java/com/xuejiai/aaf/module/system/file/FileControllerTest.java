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

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.StorageProperties;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.system.file.domain.FileRecord;
import com.xuejiai.aaf.module.system.file.service.FileRecordService;

class FileControllerTest {

    @Test
    @DisplayName("Given 文件记录包含中文原始名称 When 下载文件 Then 响应头返回编码后的原始名称")
    void should_use_encoded_original_name_when_downloading_file() {
        // 准备参数
        var fileService = mock(FileService.class);
        var storageService = mock(StorageService.class);
        var fileRecordService = mock(FileRecordService.class);
        var storageProperties = mock(StorageProperties.class);
        var controller =
                new FileController(fileService, storageService, fileRecordService, storageProperties);
        var key = "2026/07/31/stored-file.pdf";
        var originalName = "审查报告 最终版.pdf";
        var record = new FileRecord();
        record.setOriginalName(originalName);
        when(fileRecordService.requireOwnedByKey(key)).thenReturn(record);
        when(storageService.download(key)).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        // 调用
        var response = controller.download(key);

        // 断言
        var header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).isNotBlank();
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(originalName);
        verify(fileRecordService).requireOwnedByKey(key);
        verify(storageService).download(key);
    }

    @Test
    @DisplayName("Given 文件原始名称为空 When 下载文件 Then 使用存储 key 作为回退名称")
    void should_fallback_to_key_when_original_name_is_blank() {
        // 准备参数
        var fileService = mock(FileService.class);
        var storageService = mock(StorageService.class);
        var fileRecordService = mock(FileRecordService.class);
        var storageProperties = mock(StorageProperties.class);
        var controller =
                new FileController(fileService, storageService, fileRecordService, storageProperties);
        var key = "stored-file.pdf";
        var record = new FileRecord();
        record.setOriginalName(" ");
        when(fileRecordService.requireOwnedByKey(key)).thenReturn(record);
        when(storageService.download(key)).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        // 调用
        var response = controller.download(key);

        // 断言
        var header = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(key);
    }
}
