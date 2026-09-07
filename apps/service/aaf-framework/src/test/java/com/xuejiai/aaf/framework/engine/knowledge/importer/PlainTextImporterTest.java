package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlainTextImporterTest {

    private final PlainTextImporter importer = new PlainTextImporter(new DocumentImportLimits());

    @Test
    @DisplayName("Given UTF-8 纯文本 When 导入 TXT Then 生成正文段落")
    void should_import_utf8_text_as_body_section() throws Exception {
        // 准备参数
        var content = "美龄醇楂露\n配方与制作方法";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 调用
        var result = importer.importDocument(input, "美龄醇楂露.txt");

        // 断言
        assertThat(importer.supportedTypes()).containsExactly("txt");
        assertThat(result.title()).isEqualTo("美龄醇楂露.txt");
        assertThat(result.totalCharacters()).isEqualTo(content.length());
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().content()).isEqualTo(content);
        assertThat(result.sections().getFirst().level()).isZero();
        assertThat(result.sections().getFirst().metadata()).isEmpty();
    }

    @Test
    @DisplayName("Given 总字符数超过限制 When 导入 TXT Then 抛出 IOException")
    void should_throw_when_content_exceeds_max_characters() {
        // 准备参数
        var limitedImporter = new PlainTextImporter(new DocumentImportLimits(0, 0, 10, 0));
        var content = "这段内容长度超过十个字符的安全限制";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 调用 + 断言
        assertThatThrownBy(() -> limitedImporter.importDocument(input, "超限.txt"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("总字符数超过安全限制");
    }
}
