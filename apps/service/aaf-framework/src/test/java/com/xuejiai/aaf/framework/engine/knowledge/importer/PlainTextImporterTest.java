package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlainTextImporterTest {

    private final PlainTextImporter importer = new PlainTextImporter();

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
}
