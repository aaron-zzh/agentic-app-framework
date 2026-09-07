package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Word 文档导入器，基于 Apache POI。
 *
 * <p>AAF-114 #11410 加固：ZIP 炸弹检测依赖 POI 内置默认防护（{@code ZipSecureFile} 默认解压比阈值 1%，即
 * 100:1，与本设计要求的默认值一致，超限时 POI 自身抛 {@link IOException}）——{@code ZipSecureFile.setMinInflateRatio}
 * 是全局静态状态，虚拟线程并发场景下逐请求调用会互相覆盖阈值，因此不主动调用， 只在此基础上补充 POI 未覆盖的 entry 数量与总字符上限。
 */
@Component
@RequiredArgsConstructor
public class WordImporter implements DocumentImporter {

    private final DocumentImportLimits limits;

    @Override
    public Set<String> supportedTypes() {
        return Set.of("docx");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        try (var doc = new XWPFDocument(input)) {
            if (doc.getPackage().getPackageArchive().getParts().size() > limits.maxZipEntries()) {
                throw new IOException("DOCX 内部条目数量超过安全限制: " + filename);
            }
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
                if (totalChars > limits.maxCharacters()) {
                    throw new IOException("DOCX 内容总字符数超过安全限制: " + filename);
                }
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
