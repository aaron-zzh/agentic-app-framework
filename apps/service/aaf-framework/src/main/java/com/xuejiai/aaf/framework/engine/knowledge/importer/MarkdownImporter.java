package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * Markdown 文档导入器，基于 commonmark-java
 */
@Component
public class MarkdownImporter implements DocumentImporter {

    private final Parser parser = Parser.builder().build();

    @Override
    public Set<String> supportedTypes() {
        return Set.of("md", "markdown");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        var content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        var document = parser.parse(content);

        var sections = new ArrayList<DocumentSection>();
        String[] title = {filename};
        var currentText = new StringBuilder();
        int[] currentLevel = {0};

        // 遍历顶层节点，按标题分割
        var node = document.getFirstChild();
        while (node != null) {
            if (node instanceof Heading heading) {
                // 保存前一段
                if (!currentText.isEmpty()) {
                    sections.add(new DocumentSection(currentText.toString().strip(), currentLevel[0], Map.of()));
                    currentText.setLength(0);
                }
                currentLevel[0] = heading.getLevel();
                var headingText = extractText(heading);
                if (title[0].equals(filename)) {
                    title[0] = headingText;
                }
                currentText.append(headingText);
            } else {
                var text = extractText(node);
                if (!text.isBlank()) {
                    if (!currentText.isEmpty()) currentText.append("\n");
                    currentText.append(text);
                }
            }
            node = node.getNext();
        }
        // 最后一段
        if (!currentText.isEmpty()) {
            sections.add(new DocumentSection(currentText.toString().strip(), currentLevel[0], Map.of()));
        }

        long totalChars = sections.stream().mapToLong(s -> s.content().length()).sum();
        return new ImportResult(sections, title[0], totalChars);
    }

    private String extractText(Node node) {
        var sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
            }
        });
        return sb.toString();
    }
}
