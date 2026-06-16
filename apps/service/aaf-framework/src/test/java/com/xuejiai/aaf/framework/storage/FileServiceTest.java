package com.xuejiai.aaf.framework.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** FileService 单元测试（B13 上传类型/大小校验）。 */
class FileServiceTest {

    private final StorageService storage = mock(StorageService.class);
    private final FileService service =
            new FileService(storage, StorageProperties.UploadLimits.defaults());

    /** B13：超过大小上限（默认 10MB）→ 拒绝。 */
    @Test
    void upload_超大文件拒绝() {
        var big = new MockMultipartFile("file", "big.png", "image/png", new byte[11 * 1024 * 1024]);
        assertThatThrownBy(() -> service.upload(big))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("大小");
    }

    /** B13：非白名单类型（可执行脚本）→ 拒绝（防存储型 XSS/滥用）。 */
    @Test
    void upload_非白名单类型拒绝() {
        var sh = new MockMultipartFile("file", "x.sh", "application/x-sh", "echo hi".getBytes());
        assertThatThrownBy(() -> service.upload(sh))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("类型");
    }

    /** 合法图片正常上传。 */
    @Test
    void upload_合法文件通过() {
        when(storage.upload(any(), any(), any())).thenReturn("2026/05/30/abc.png");
        when(storage.getUrl("2026/05/30/abc.png")).thenReturn("/files/2026/05/30/abc.png");
        var ok = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        var vo = service.upload(ok);

        assertThat(vo.key()).isEqualTo("2026/05/30/abc.png");
    }
}
