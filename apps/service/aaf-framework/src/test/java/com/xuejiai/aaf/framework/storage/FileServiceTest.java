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

    /** AIGC 视频结果使用 video/mp4 时应通过默认白名单。 */
    @Test
    void upload_videoMp4允许() {
        when(storage.upload(any(), any(), any())).thenReturn("aigc/video.mp4");
        when(storage.getUrl("aigc/video.mp4")).thenReturn("/files/aigc/video.mp4");
        var video = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[] {0, 1});

        var file = service.upload(video);

        assertThat(file.contentType()).isEqualTo("video/mp4");
    }

    /** B13：SVG 属主动内容，即使伪装扩展名也应拒绝（存储型 XSS）。 */
    @Test
    void upload_svg主动内容拒绝() {
        var svg =
                new MockMultipartFile(
                        "file", "x.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes());
        assertThatThrownBy(() -> service.upload(svg)).isInstanceOf(StorageException.class);
    }

    /** B13：byte[] 入口以前完全绕过校验，现在同样受类型白名单约束。 */
    @Test
    void uploadFromBytes_非白名单类型拒绝() {
        assertThatThrownBy(
                        () ->
                                service.uploadFromBytes(
                                        "x".getBytes(), "a/b/x.sh", "application/x-sh"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("类型");
    }

    /** B13：byte[] 入口超过大小上限同样拒绝。 */
    @Test
    void uploadFromBytes_超大拒绝() {
        assertThatThrownBy(
                        () ->
                                service.uploadFromBytes(
                                        new byte[11 * 1024 * 1024], "a/b/x.png", "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("大小");
    }

    /** B13：原始图片 Base64 无 data URL 前缀时，按文件签名识别 MIME。 */
    @Test
    void uploadFromBase64_无dataUrl时按png签名推断类型() {
        when(storage.upload(any(), any(), any())).thenReturn("aigc/image.png");
        when(storage.getUrl("aigc/image.png")).thenReturn("/files/aigc/image.png");
        var pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var rawBase64 = java.util.Base64.getEncoder().encodeToString(pngBytes);

        var file = service.uploadFromBase64(rawBase64, "aigc/image.png");

        assertThat(file.contentType()).isEqualTo("image/png");
    }

    /** B13：Base64 入口以 data URL 声明 HTML 时应被主动内容规则拦住。 */
    @Test
    void uploadFromBase64_html主动内容拒绝() {
        var dataUrl =
                "data:text/html;base64,"
                        + java.util.Base64.getEncoder()
                                .encodeToString("<script>alert(1)</script>".getBytes());
        assertThatThrownBy(() -> service.uploadFromBase64(dataUrl, "a/b/x.html"))
                .isInstanceOf(StorageException.class);
    }
}
