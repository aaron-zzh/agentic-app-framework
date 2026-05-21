package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** 递归字符分块器，按分隔符层级递归分割 */
@Component
public class RecursiveCharacterChunker implements DocumentChunker {

    @Override
    public ChunkStrategy strategy() {
        return ChunkStrategy.RECURSIVE_CHARACTER;
    }

    @Override
    public List<DocumentChunk> chunk(
            String content, ChunkConfig config, Map<String, Object> baseMetadata) {
        var texts = splitRecursive(content, config.separators(), config.chunkSize());
        // 合并重叠窗口
        var chunks = new ArrayList<DocumentChunk>();
        int index = 0;
        for (var text : texts) {
            var metadata = new HashMap<>(baseMetadata);
            metadata.put("chunk_index", index);
            chunks.add(
                    new DocumentChunk(
                            text, index, metadata, FixedSizeChunker.estimateTokenCount(text)));
            index++;
        }
        return chunks;
    }

    private List<String> splitRecursive(String text, List<String> separators, int chunkSize) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        if (separators.isEmpty()) {
            // 无分隔符可用，强制按 chunkSize 切割
            var result = new ArrayList<String>();
            for (int i = 0; i < text.length(); i += chunkSize) {
                result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return result;
        }

        var separator = separators.getFirst();
        var remaining = separators.subList(1, separators.size());
        var parts = text.split(java.util.regex.Pattern.quote(separator), -1);

        var result = new ArrayList<String>();
        var current = new StringBuilder();

        for (var part : parts) {
            if (current.isEmpty()) {
                current.append(part);
            } else if (current.length() + separator.length() + part.length() <= chunkSize) {
                current.append(separator).append(part);
            } else {
                // 当前块已满，递归处理后加入结果
                result.addAll(splitRecursive(current.toString(), remaining, chunkSize));
                current = new StringBuilder(part);
            }
        }
        if (!current.isEmpty()) {
            result.addAll(splitRecursive(current.toString(), remaining, chunkSize));
        }
        return result;
    }
}
