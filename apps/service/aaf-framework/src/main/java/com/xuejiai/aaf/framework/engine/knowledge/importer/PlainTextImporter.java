package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** TXT 纯文本文档导入器，按 UTF-8 解码为正文段落。 */
@Component
public class PlainTextImporter implements DocumentImporter {

    @Override
    public Set<String> supportedTypes() {
        return Set.of("txt");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        var content = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        var sections =
                content.isEmpty()
                        ? List.<DocumentSection>of()
                        : List.of(new DocumentSection(content, 0, Map.of()));
        return new ImportResult(sections, filename, content.length());
    }
}
