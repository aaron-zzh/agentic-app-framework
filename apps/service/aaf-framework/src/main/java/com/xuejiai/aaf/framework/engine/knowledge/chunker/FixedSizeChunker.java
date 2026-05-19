package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 固定长度分块器，支持重叠窗口
 */
@Component
public class FixedSizeChunker implements DocumentChunker {

    @Override
    public ChunkStrategy strategy() {
        return ChunkStrategy.FIXED_SIZE;
    }

    @Override
    public List<DocumentChunk> chunk(String content, ChunkConfig config, Map<String, Object> baseMetadata) {
        var chunks = new ArrayList<DocumentChunk>();
        int len = content.length();
        int step = config.chunkSize() - config.overlapSize();
        int index = 0;

        for (int start = 0; start < len; start += step) {
            int end = Math.min(start + config.chunkSize(), len);
            var text = content.substring(start, end);
            chunks.add(buildChunk(text, index++, baseMetadata));
            if (end == len) break;
        }
        return chunks;
    }

    private DocumentChunk buildChunk(String text, int index, Map<String, Object> baseMetadata) {
        var metadata = new HashMap<>(baseMetadata);
        metadata.put("chunk_index", index);
        return new DocumentChunk(text, index, metadata, estimateTokenCount(text));
    }

    static int estimateTokenCount(String text) {
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.toString(c).matches("[\\u4e00-\\u9fff]")) {
                count++;
                inWord = false;
            } else if (c == ' ' || c == '\n' || c == '\t') {
                inWord = false;
            } else {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            }
        }
        return count;
    }
}
