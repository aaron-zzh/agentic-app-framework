package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/** 按段落和句末标点边界分块，并在相邻块间保留重叠上下文；切分保留原文换行与空白，不改写内容。 */
@Component
public class SemanticBoundaryChunker implements DocumentChunker {

    /** 句末标点连同其后空白、后接空白或结尾的英文句点（不拆小数）、段落空行，均作为片段结尾。 */
    private static final Pattern BOUNDARY =
            Pattern.compile("[。！？!?；;]\\s*|\\.(?:\\s+|$)|(?:\\r?\\n){2,}");

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
            chunks.add(new DocumentChunk(text, index, metadata, TokenEstimator.estimate(text)));
        }
        return chunks;
    }

    /** 均衡打包片段：先按块大小估算块数，以总长除以块数为目标长度，块达到目标长度后在下一个边界处结束，任何块都不超过块大小，避免末尾出现很短的尾块。 */
    private List<String> semanticTexts(String content, int chunkSize, int overlapSize) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        var parts = Math.max(1, (int) Math.ceil((double) content.length() / chunkSize));
        var target = (int) Math.ceil((double) content.length() / parts);
        for (var segment : segments(content)) {
            if (segment.length() > chunkSize) {
                flush(result, current);
                appendWindows(result, segment, chunkSize, overlapSize);
                continue;
            }
            if (!current.toString().isBlank()
                    && (current.length() >= target
                            || current.length() + segment.length() > chunkSize)) {
                flush(result, current);
                if (!result.isEmpty()) {
                    appendOverlap(
                            current, result.getLast(), segment.length(), chunkSize, overlapSize);
                }
            }
            current.append(segment);
        }
        flush(result, current);
        return result;
    }

    /** 按边界切出片段，每个片段带上自身的结尾标点与空白，片段依次拼接即为原文。 */
    private List<String> segments(String content) {
        var segments = new ArrayList<String>();
        var matcher = BOUNDARY.matcher(content);
        var start = 0;
        while (matcher.find()) {
            if (matcher.end() > start) {
                segments.add(content.substring(start, matcher.end()));
                start = matcher.end();
            }
        }
        if (start < content.length()) {
            segments.add(content.substring(start));
        }
        return segments;
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
        if (!current.toString().isBlank()) {
            result.add(current.toString());
        }
        current.setLength(0);
    }
}
