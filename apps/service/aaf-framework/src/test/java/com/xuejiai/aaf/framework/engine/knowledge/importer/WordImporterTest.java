package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WordImporterTest {

    private final WordImporter importer = new WordImporter(new DocumentImportLimits());

    @Test
    @DisplayName("Given 含标题与正文的 DOCX When 导入 Then 生成对应层级段落")
    void should_import_docx_with_heading_and_body() throws Exception {
        // 准备参数
        var docxBytes = buildDocx("正文内容一行");

        // 调用
        var result = importer.importDocument(new ByteArrayInputStream(docxBytes), "样例.docx");

        // 断言
        assertThat(importer.supportedTypes()).containsExactly("docx");
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().content()).isEqualTo("正文内容一行");
    }

    @Test
    @DisplayName("Given 总字符数超过限制 When 导入 DOCX Then 抛出 IOException")
    void should_throw_when_characters_exceed_limit() throws Exception {
        // 准备参数：限制总字符数为 5，实际正文远超此长度
        var limitedImporter = new WordImporter(new DocumentImportLimits(0, 0, 5, 0));
        var docxBytes = buildDocx("这是一段超过五个字符限制的正文内容");

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                limitedImporter.importDocument(
                                        new ByteArrayInputStream(docxBytes), "超限.docx"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("总字符数超过安全限制");
    }

    @Test
    @DisplayName("Given entry 数量超过限制 When 导入 DOCX Then 抛出 IOException")
    void should_throw_when_zip_entries_exceed_limit() throws Exception {
        // 准备参数：一个标准 DOCX 至少包含 content types/document/styles 等数个内部条目，限制为 1 必定超限
        var limitedImporter = new WordImporter(new DocumentImportLimits(0, 0, 0, 1));
        var docxBytes = buildDocx("任意正文");

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                limitedImporter.importDocument(
                                        new ByteArrayInputStream(docxBytes), "超限.docx"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("条目数量超过安全限制");
    }

    private byte[] buildDocx(String bodyText) throws IOException {
        try (var doc = new XWPFDocument();
                var output = new ByteArrayOutputStream()) {
            var paragraph = doc.createParagraph();
            var run = paragraph.createRun();
            run.setText(bodyText);
            doc.write(output);
            return output.toByteArray();
        }
    }
}
