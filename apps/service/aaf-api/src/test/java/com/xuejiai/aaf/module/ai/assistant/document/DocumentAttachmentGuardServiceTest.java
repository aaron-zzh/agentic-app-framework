package com.xuejiai.aaf.module.ai.assistant.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentImportLimits;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.importer.PlainTextImporter;
import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort;
import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort.ScanResult;
import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort.ScanStatus;
import com.xuejiai.aaf.module.ai.assistant.document.DocumentAttachmentGuardService.DocumentAttachmentLimits;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DocumentAttachmentGuardServiceTest extends BaseMockitoUnitTest {

    @Mock private FileStoragePort fileStoragePort;
    @Mock private FileSecurityScanPort fileSecurityScanPort;

    private ImporterFactory importerFactory;
    private DocumentAttachmentGuardService guard;

    private static final String FILE_KEY = "users/1/document.txt";
    private static final byte[] CONTENT = "文本内容".getBytes(StandardCharsets.UTF_8);

    private void initGuard() {
        importerFactory =
                new ImporterFactory(List.of(new PlainTextImporter(new DocumentImportLimits())));
        guard =
                new DocumentAttachmentGuardService(
                        fileStoragePort,
                        fileSecurityScanPort,
                        importerFactory,
                        new DocumentAttachmentLimits(20L * 1024 * 1024, 5));
    }

    private StoredFile storedFile(String originalName, String mimeType, long size) {
        return new StoredFile(
                1L, FILE_KEY, "http://example.com/f", originalName, mimeType, size, "hash", 1L);
    }

    @Test
    @DisplayName("Given 不支持的扩展名 When 门禁校验 Then 抛出类型不支持异常")
    void should_throw_when_extension_unsupported() {
        // 准备参数
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("恶意脚本.exe", "application/octet-stream", 10));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given MIME 与扩展名不一致 When 门禁校验 Then 抛出类型不一致异常")
    void should_throw_when_mime_mismatches_extension() {
        // 准备参数
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "application/pdf", 10));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 文件大小超过限制 When 门禁校验 Then 抛出超限异常")
    void should_throw_when_size_exceeds_limit() {
        // 准备参数：限制为 10 字节，实际声明大小 100 字节
        importerFactory =
                new ImporterFactory(List.of(new PlainTextImporter(new DocumentImportLimits())));
        guard =
                new DocumentAttachmentGuardService(
                        fileStoragePort,
                        fileSecurityScanPort,
                        importerFactory,
                        new DocumentAttachmentLimits(10, 5));
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "text/plain", 100));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 魔数与扩展名不一致 When 门禁校验 Then 抛出类型不一致异常")
    void should_throw_when_magic_bytes_mismatch() {
        // 准备参数：扩展名为 txt 但实际内容是非法 UTF-8 字节
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "text/plain", 4));
        when(fileStoragePort.openByKey(FILE_KEY))
                .thenReturn(
                        new ByteArrayInputStream(
                                new byte[] {(byte) 0xff, (byte) 0xfe, 0x00, 0x01}));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 安全扫描未通过 When 门禁校验 Then 抛出扫描拒绝异常")
    void should_throw_when_scan_rejected() {
        // 准备参数
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "text/plain", CONTENT.length));
        when(fileStoragePort.openByKey(FILE_KEY)).thenReturn(new ByteArrayInputStream(CONTENT));
        when(fileSecurityScanPort.scan(any(), any(), any()))
                .thenReturn(new ScanResult(ScanStatus.INFECTED, "scanner", "1.0", "检出风险"));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 扫描服务不可用 When 门禁校验 Then 不降级放行并抛出异常")
    void should_throw_when_scan_unavailable() {
        // 准备参数
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "text/plain", CONTENT.length));
        when(fileStoragePort.openByKey(FILE_KEY)).thenReturn(new ByteArrayInputStream(CONTENT));
        when(fileSecurityScanPort.scan(any(), any(), any()))
                .thenReturn(new ScanResult(ScanStatus.UNAVAILABLE, "scanner", "1.0", "服务不可用"));

        // 调用 + 断言
        assertThatThrownBy(() -> guard.guardAndParse(FILE_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 全部校验通过 When 门禁校验 Then 返回解析结果与 provenance 元信息")
    void should_return_guarded_document_when_all_checks_pass() {
        // 准备参数
        initGuard();
        when(fileStoragePort.requireCurrentOwnerByKey(FILE_KEY))
                .thenReturn(storedFile("文档.txt", "text/plain", CONTENT.length));
        when(fileStoragePort.openByKey(FILE_KEY)).thenReturn(new ByteArrayInputStream(CONTENT));
        when(fileSecurityScanPort.scan(any(), any(), any()))
                .thenReturn(new ScanResult(ScanStatus.CLEAN, "scanner", "1.0", null));

        // 调用
        var guarded = guard.guardAndParse(FILE_KEY);

        // 断言
        assertThat(guarded.resourceId()).isEqualTo(FILE_KEY);
        assertThat(guarded.contentHash()).isEqualTo("hash");
        assertThat(guarded.importerName()).isEqualTo("PlainTextImporter");
        assertThat(guarded.result().sections()).isNotEmpty();
    }
}
