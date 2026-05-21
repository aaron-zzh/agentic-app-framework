package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

/** Word 文档导入器，基于 Apache POI */
@Component
public class WordImporter implements DocumentImporter {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("docx");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        try (var doc = new XWPFDocument(input)) {
            var sections = new ArrayList<DocumentSection>();
            String title = filename;
            long totalChars = 0;

            for (var para : doc.getParagraphs()) {
                var text = para.getText().strip();
                if (text.isEmpty()) continue;

                int level = extractLevel(para);
                if (title.equals(filename) && level > 0) {
                    title = text;
                }
                sections.add(new DocumentSection(text, level, Map.of()));
                totalChars += text.length();
            }

            return new ImportResult(sections, title, totalChars);
        }
    }

    private int extractLevel(XWPFParagraph para) {
        var style = para.getStyle();
        if (style != null) {
            // Word 标题样式通常为 "Heading1"、"Heading2" 或 "heading 1" 等
            var matcher = java.util.regex.Pattern.compile("(?i)heading\\s*(\\d+)").matcher(style);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }
}
