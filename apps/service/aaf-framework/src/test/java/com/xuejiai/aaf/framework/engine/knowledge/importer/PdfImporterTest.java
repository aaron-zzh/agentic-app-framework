package com.xuejiai.aaf.framework.engine.knowledge.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PdfImporterTest {

    private final PdfImporter importer = new PdfImporter(new DocumentImportLimits());

    @Test
    @DisplayName("Given 单页 PDF When 导入 Then 生成对应页码段落")
    void should_import_single_page_pdf() throws Exception {
        // 准备参数
        var pdfBytes = buildPdf(1, "Page one body text");

        // 调用
        var result = importer.importDocument(new ByteArrayInputStream(pdfBytes), "样例.pdf");

        // 断言
        assertThat(importer.supportedTypes()).containsExactly("pdf");
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().getFirst().metadata()).containsEntry("page_number", 1);
    }

    @Test
    @DisplayName("Given 页数超过限制 When 导入 PDF Then 抛出 IOException")
    void should_throw_when_pages_exceed_limit() throws Exception {
        // 准备参数：限制最多 1 页，实际生成 2 页
        var limitedImporter = new PdfImporter(new DocumentImportLimits(1, 0, 0, 0));
        var pdfBytes = buildPdf(2, "Body text content");

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                limitedImporter.importDocument(
                                        new ByteArrayInputStream(pdfBytes), "超限.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PDF 解析失败");
    }

    @Test
    @DisplayName("Given 总字符数超过限制 When 导入 PDF Then 抛出 IOException")
    void should_throw_when_characters_exceed_limit() throws Exception {
        // 准备参数：限制总字符数为 5，实际正文远超此长度
        var limitedImporter = new PdfImporter(new DocumentImportLimits(0, 0, 5, 0));
        var pdfBytes = buildPdf(1, "This body text exceeds the five character limit");

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                limitedImporter.importDocument(
                                        new ByteArrayInputStream(pdfBytes), "超限.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PDF 解析失败");
    }

    private byte[] buildPdf(int pageCount, String text) throws IOException {
        try (var doc = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            for (var i = 0; i < pageCount; i++) {
                var page = new PDPage();
                doc.addPage(page);
                try (var content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            doc.save(output);
            return output.toByteArray();
        }
    }
}
