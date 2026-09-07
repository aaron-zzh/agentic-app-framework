package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HtmlImporterTest {

    private final HtmlImporter importer = new HtmlImporter(new DocumentImportLimits());

    @Test
    @DisplayName("Given 含 title 标签的 HTML When 导入 Then 提取标题与正文")
    void should_import_html_title_and_body() throws Exception {
        // 准备参数
        var html =
                "<html><head><title>页面标题</title></head><body><h1>一级标题</h1><p>正文内容</p></body></html>";
        var input = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));

        // 调用
        var result = importer.importDocument(input, "样例.html");

        // 断言
        assertThat(importer.supportedTypes()).containsExactlyInAnyOrder("html", "htm");
        assertThat(result.title()).isEqualTo("页面标题");
        assertThat(result.sections()).isNotEmpty();
    }

    @Test
    @DisplayName("Given 总字符数超过限制 When 导入 HTML Then 抛出 IOException")
    void should_throw_when_characters_exceed_limit() {
        // 准备参数：限制总字符数为 5，实际正文远超此长度
        var limitedImporter = new HtmlImporter(new DocumentImportLimits(0, 0, 5, 0));
        var html = "<html><body><p>这是一段超过五个字符限制的正文内容</p></body></html>";
        var input = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));

        // 调用 + 断言
        assertThatThrownBy(() -> limitedImporter.importDocument(input, "超限.html"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("总字符数超过安全限制");
    }
}
