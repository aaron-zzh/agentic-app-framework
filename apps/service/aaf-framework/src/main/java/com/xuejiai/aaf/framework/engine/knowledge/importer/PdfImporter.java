package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF 文档导入器，基于 Apache PDFBox
 */
@Component
public class PdfImporter implements DocumentImporter {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("pdf");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        try (var doc = Loader.loadPDF(new RandomAccessReadBuffer(input))) {
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
                }
            }

            var title = extractTitle(doc, filename);
            return new ImportResult(sections, title, totalChars);
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
