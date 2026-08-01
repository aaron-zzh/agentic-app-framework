package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 按段落和句末标点边界分块，并在相邻块间保留重叠上下文。 */
@Component
public class SemanticBoundaryChunker implements DocumentChunker {

    private static final Pattern BOUNDARY = Pattern.compile("(?<=[。！？!?；;.])\\s*|(?:\\r?\\n){2,}");

    @Override
    public ChunkStrategy strategy() {
        return ChunkStrategy.SEMANTIC_BOUNDARY;
    }

    @Override
    public List<DocumentChunk> chunk(
            String content, ChunkConfig config, Map<String, Object> baseMetadata) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        var texts = semanticTexts(content, config.chunkSize(), config.overlapSize());
        var chunks = new ArrayList<DocumentChunk>(texts.size());
        for (var index = 0; index < texts.size(); index++) {
            var text = texts.get(index);
            var metadata = new HashMap<>(baseMetadata);
            metadata.put("chunk_index", index);
            chunks.add(
                    new DocumentChunk(
                            text, index, metadata, FixedSizeChunker.estimateTokenCount(text)));
        }
        return chunks;
    }

    private List<String> semanticTexts(String content, int chunkSize, int overlapSize) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        for (var segment : BOUNDARY.split(content)) {
            if (segment.isBlank()) {
                continue;
            }
            if (segment.length() > chunkSize) {
                flush(result, current);
                appendWindows(result, segment, chunkSize, overlapSize);
                continue;
            }
            if (!current.isEmpty() && current.length() + segment.length() > chunkSize) {
                flush(result, current);
                appendOverlap(current, result.getLast(), segment.length(), chunkSize, overlapSize);
            }
            current.append(segment);
        }
        flush(result, current);
        return result;
    }

    private void appendWindows(List<String> result, String text, int chunkSize, int overlapSize) {
        var step = chunkSize - overlapSize;
        for (var start = 0; start < text.length(); start += step) {
            var end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
    }

    private void appendOverlap(
            StringBuilder target, String previous, int nextLength, int chunkSize, int overlapSize) {
        var available = Math.max(0, chunkSize - nextLength);
        var length = Math.min(Math.min(overlapSize, available), previous.length());
        if (length > 0) {
            target.append(previous, previous.length() - length, previous.length());
        }
    }

    private void flush(List<String> result, StringBuilder current) {
        if (!current.isEmpty()) {
            result.add(current.toString());
            current.setLength(0);
        }
    }
}
