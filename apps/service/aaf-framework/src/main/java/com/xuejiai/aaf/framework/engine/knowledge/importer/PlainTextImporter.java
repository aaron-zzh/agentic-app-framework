package com.xuejiai.aaf.framework.engine.knowledge.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * TXT 纯文本文档导入器，按 UTF-8 解码为正文段落。
 *
 * <p>AAF-114 #11410 加固：总字符数超过 {@link DocumentImportLimits#maxCharacters()} 拒绝返回结果。
 */
@Component
@RequiredArgsConstructor
public class PlainTextImporter implements DocumentImporter {

    private final DocumentImportLimits limits;

    @Override
    public Set<String> supportedTypes() {
        return Set.of("txt");
    }

    @Override
    public ImportResult importDocument(InputStream input, String filename) throws IOException {
        var content = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        if (content.length() > limits.maxCharacters()) {
            throw new IOException("TXT 内容总字符数超过安全限制: " + filename);
        }
        var sections =
                content.isEmpty()
                        ? List.<DocumentSection>of()
                        : List.of(new DocumentSection(content, 0, Map.of()));
        return new ImportResult(sections, filename, content.length());
    }
}
