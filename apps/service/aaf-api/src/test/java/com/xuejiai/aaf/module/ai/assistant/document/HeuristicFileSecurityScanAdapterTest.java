package com.xuejiai.aaf.module.ai.assistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.engine.knowledge.security.FileSecurityScanPort.ScanStatus;

class HeuristicFileSecurityScanAdapterTest {

    private final HeuristicFileSecurityScanAdapter adapter = new HeuristicFileSecurityScanAdapter();

    @Test
    @DisplayName("Given 正常文本内容 When 扫描 Then 判定为 CLEAN")
    void should_return_clean_when_content_is_normal() {
        // 准备参数
        var content = "普通文本内容".getBytes(StandardCharsets.UTF_8);

        // 调用
        var result = adapter.scan(content, "文档.txt", "text/plain");

        // 断言
        assertThat(result.status()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("Given 空内容 When 扫描 Then 判定为 ERROR")
    void should_return_error_when_content_is_empty() {
        // 调用
        var result = adapter.scan(new byte[0], "空文件.txt", "text/plain");

        // 断言
        assertThat(result.status()).isEqualTo(ScanStatus.ERROR);
    }

    @Test
    @DisplayName("Given 含 DOCTYPE 声明的内容 When 扫描 Then 判定为 INFECTED")
    void should_return_infected_when_doctype_marker_present() {
        // 准备参数
        var content =
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        .getBytes(StandardCharsets.UTF_8);

        // 调用
        var result = adapter.scan(content, "恶意.html", "text/html");

        // 断言
        assertThat(result.status()).isEqualTo(ScanStatus.INFECTED);
    }

    @Test
    @DisplayName("Given 解压比超过安全阈值的 ZIP 内容 When 扫描 Then 判定为 INFECTED")
    void should_return_infected_when_zip_inflate_ratio_exceeds_threshold() throws Exception {
        // 准备参数：大量重复字节使 DEFLATE 压缩率极高，模拟 zip bomb 特征（压缩后体积远小于解压后体积）
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.setLevel(9);
            zip.putNextEntry(new ZipEntry("bomb.txt"));
            var chunk = new byte[1024 * 1024];
            for (var i = 0; i < 50; i++) {
                zip.write(chunk);
            }
            zip.closeEntry();
        }

        // 调用
        var result = adapter.scan(output.toByteArray(), "炸弹.docx", "application/zip");

        // 断言
        assertThat(result.status()).isEqualTo(ScanStatus.INFECTED);
    }

    @Test
    @DisplayName("Given 正常 ZIP 内容 When 扫描 Then 判定为 CLEAN")
    void should_return_clean_when_zip_content_is_normal() throws Exception {
        // 准备参数
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            var entry = new ZipEntry("normal.txt");
            zip.putNextEntry(entry);
            var body = "正常内容".getBytes(StandardCharsets.UTF_8);
            zip.write(body);
            zip.closeEntry();
        }

        // 调用
        var result = adapter.scan(output.toByteArray(), "正常.docx", "application/zip");

        // 断言
        assertThat(result.status()).isEqualTo(ScanStatus.CLEAN);
    }
}
