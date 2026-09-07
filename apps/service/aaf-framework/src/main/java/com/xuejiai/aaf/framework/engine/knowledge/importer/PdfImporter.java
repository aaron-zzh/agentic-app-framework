package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * PDF 文档导入器，基于 Apache PDFBox。
 *
 * <p>AAF-114 #11410 加固：页数超过 {@link DocumentImportLimits#maxPdfPages()} 拒绝解析；总解析耗时超过 {@link
 * DocumentImportLimits#parseTimeoutMillis()} 中断并报错；解析后总字符数超过 {@link
 * DocumentImportLimits#maxCharacters()} 拒绝返回结果。
 */
@Component
@RequiredArgsConstructor
public class PdfImporter implements DocumentImporter {

    private final DocumentImportLimits limits;

    @Override
    public Set<String> supportedTypes() {
        return Set.of("pdf");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        var future = CompletableFuture.supplyAsync(() -> parse(input, filename));
        try {
            return future.get(limits.parseTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            future.cancel(true);
            throw new IOException("PDF 解析超时: " + filename, failure);
        } catch (ExecutionException failure) {
            var cause = failure.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("PDF 解析失败: " + filename, cause);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("PDF 解析被中断: " + filename, failure);
        }
    }

    private ImportResult parse(InputStream input, String filename) {
        try (var doc = Loader.loadPDF(new RandomAccessReadBuffer(input))) {
            if (doc.getNumberOfPages() > limits.maxPdfPages()) {
                throw new IllegalStateException("PDF 页数超过安全限制: " + filename);
            }
            var sections = new ArrayList<DocumentSection>();
            var stripper = new PDFTextStripper();
            long totalChars = 0;

            for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                var text = stripper.getText(doc).strip();
                if (!text.isEmpty()) {
                    sections.add(new DocumentSection(text, 0, Map.of("page_number", i)));
                    totalChars += text.length();
                    if (totalChars > limits.maxCharacters()) {
                        throw new IllegalStateException("PDF 内容总字符数超过安全限制: " + filename);
                    }
                }
            }

            var title = extractTitle(doc, filename);
            return new ImportResult(sections, title, totalChars);
        } catch (IOException failure) {
            throw new IllegalStateException("PDF 解析失败: " + filename, failure);
        }
    }

    private String extractTitle(PDDocument doc, String filename) {
        var info = doc.getDocumentInformation();
        if (info != null && info.getTitle() != null && !info.getTitle().isBlank()) {
            return info.getTitle();
        }
        return filename;
    }
}
