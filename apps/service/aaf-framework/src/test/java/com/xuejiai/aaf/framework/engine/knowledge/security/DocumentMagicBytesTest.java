package com.xuejiai.aaf.framework.engine.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentMagicBytesTest {

    @Test
    @DisplayName("Given PDF 魔数字节 When 校验 pdf 扩展名 Then 返回 true")
    void should_return_true_when_pdf_magic_matches() {
        // 准备参数
        var content = "%PDF-1.7\n其余内容".getBytes(StandardCharsets.US_ASCII);

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "pdf")).isTrue();
    }

    @Test
    @DisplayName("Given 非 PDF 内容 When 校验 pdf 扩展名 Then 返回 false")
    void should_return_false_when_pdf_magic_mismatches() {
        // 准备参数
        var content = "这不是 PDF 文件".getBytes(StandardCharsets.UTF_8);

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "pdf")).isFalse();
    }

    @Test
    @DisplayName("Given ZIP 魔数字节 When 校验 docx 扩展名 Then 返回 true")
    void should_return_true_when_docx_magic_matches() {
        // 准备参数
        var content = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x00, 0x00};

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "docx")).isTrue();
    }

    @Test
    @DisplayName("Given 非 ZIP 内容 When 校验 docx 扩展名 Then 返回 false")
    void should_return_false_when_docx_magic_mismatches() {
        // 准备参数
        var content = "普通文本".getBytes(StandardCharsets.UTF_8);

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "docx")).isFalse();
    }

    @Test
    @DisplayName("Given UTF-8 可解码文本 When 校验 txt/md/html 扩展名 Then 返回 true")
    void should_return_true_when_plain_text_decodable() {
        // 准备参数
        var content = "普通文本内容".getBytes(StandardCharsets.UTF_8);

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "txt")).isTrue();
        assertThat(DocumentMagicBytes.matches(content, "md")).isTrue();
        assertThat(DocumentMagicBytes.matches(content, "html")).isTrue();
    }

    @Test
    @DisplayName("Given 非法 UTF-8 字节序列 When 校验 txt 扩展名 Then 返回 false")
    void should_return_false_when_plain_text_not_decodable() {
        // 准备参数：0xFF 0xFE 不是合法 UTF-8 序列
        var content = new byte[] {(byte) 0xff, (byte) 0xfe, 0x00, 0x01};

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "txt")).isFalse();
    }

    @Test
    @DisplayName("Given 不支持的扩展名 When 校验 Then 返回 false")
    void should_return_false_when_extension_unsupported() {
        // 准备参数
        var content = "任意内容".getBytes(StandardCharsets.UTF_8);

        // 调用 + 断言
        assertThat(DocumentMagicBytes.matches(content, "exe")).isFalse();
    }
}
