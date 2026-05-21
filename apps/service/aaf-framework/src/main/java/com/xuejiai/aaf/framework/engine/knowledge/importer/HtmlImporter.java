package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** HTML 文档导入器，基于 Jsoup */
@Component
public class HtmlImporter implements DocumentImporter {

    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");

    @Override
    public Set<String> supportedTypes() {
        return Set.of("html", "htm");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        var html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        var doc = Jsoup.parse(html);
        var body = doc.body();

        var sections = new ArrayList<DocumentSection>();
        var currentText = new StringBuilder();
        int currentLevel = 0;

        for (var child : body.children()) {
            var tag = child.tagName().toLowerCase();
            if (HEADING_TAGS.contains(tag)) {
                // 保存前一段
                if (!currentText.isEmpty()) {
                    sections.add(
                            new DocumentSection(
                                    currentText.toString().strip(), currentLevel, Map.of()));
                    currentText.setLength(0);
                }
                currentLevel = Integer.parseInt(tag.substring(1));
                currentText.append(child.text());
            } else {
                var text = child.text();
                if (!text.isBlank()) {
                    if (!currentText.isEmpty()) currentText.append("\n");
                    currentText.append(text);
                }
            }
        }
        if (!currentText.isEmpty()) {
            sections.add(
                    new DocumentSection(currentText.toString().strip(), currentLevel, Map.of()));
        }

        // 提取标题
        var titleEl = doc.selectFirst("title");
        var title = (titleEl != null && !titleEl.text().isBlank()) ? titleEl.text() : filename;

        long totalChars = sections.stream().mapToLong(s -> s.content().length()).sum();
        return new ImportResult(sections, title, totalChars);
    }
}
