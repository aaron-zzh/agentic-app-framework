package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownImporterTest {

    private final MarkdownImporter importer = new MarkdownImporter(new DocumentImportLimits());

    @Test
    @DisplayName("Given 含一级标题的 Markdown When 导入 Then 标题作为文档标题")
    void should_import_markdown_heading_as_title() throws Exception {
        // 准备参数
        var content = "# 主标题\n正文内容";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 调用
        var result = importer.importDocument(input, "样例.md");

        // 断言
        assertThat(importer.supportedTypes()).containsExactlyInAnyOrder("md", "markdown");
        assertThat(result.title()).isEqualTo("主标题");
    }

    @Test
    @DisplayName("Given 总字符数超过限制 When 导入 Markdown Then 抛出 IOException")
    void should_throw_when_characters_exceed_limit() {
        // 准备参数：限制总字符数为 5，实际正文远超此长度
        var limitedImporter = new MarkdownImporter(new DocumentImportLimits(0, 0, 5, 0));
        var content = "这是一段超过五个字符限制的正文内容";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // 调用 + 断言
        assertThatThrownBy(() -> limitedImporter.importDocument(input, "超限.md"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("总字符数超过安全限制");
    }
}
