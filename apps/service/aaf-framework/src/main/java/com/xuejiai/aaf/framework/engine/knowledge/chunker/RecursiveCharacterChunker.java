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
        var texts =
                splitRecursive(
                        content, config.separators(), config.chunkSize(), config.overlapSize());
        var chunks = new ArrayList<DocumentChunk>();
        String previous = null;
        int index = 0;
        for (var text : texts) {
            var effectiveText = withOverlap(previous, text, config);
            var metadata = new HashMap<>(baseMetadata);
            metadata.put("chunk_index", index);
            chunks.add(
                    new DocumentChunk(
                            effectiveText,
                            index,
                            metadata,
                            FixedSizeChunker.estimateTokenCount(effectiveText)));
            previous = effectiveText;
            index++;
        }
        return chunks;
    }

    private String withOverlap(String previous, String current, ChunkConfig config) {
        if (previous == null
                || config.overlapSize() == 0
                || current.length() >= config.chunkSize()) {
            return current;
        }
        var available = config.chunkSize() - current.length();
        var overlap = Math.min(Math.min(config.overlapSize(), available), previous.length());
        return overlap == 0 ? current : previous.substring(previous.length() - overlap) + current;
    }

    private List<String> splitRecursive(
            String text, List<String> separators, int chunkSize, int overlapSize) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        if (separators.isEmpty()) {
            var result = new ArrayList<String>();
            var step = chunkSize - overlapSize;
            for (int start = 0; start < text.length(); start += step) {
                var end = Math.min(start + chunkSize, text.length());
                result.add(text.substring(start, end));
                if (end == text.length()) {
                    break;
                }
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
                result.addAll(
                        splitRecursive(current.toString(), remaining, chunkSize, overlapSize));
                current = new StringBuilder(part);
            }
        }
        if (!current.isEmpty()) {
            result.addAll(splitRecursive(current.toString(), remaining, chunkSize, overlapSize));
        }
        return result;
    }
}
