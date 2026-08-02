package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.StoredChunk;

/** 为事实抽取组装焦点块及最小必要邻块上下文。 */
@Component
public class FocusExtractionContextAssembler {

    private static final int DEFAULT_NEIGHBOR_LIMIT = 400;

    public FocusContext assemble(List<StoredChunk> chunks, int focusIndex, int characterBudget) {
        if (focusIndex < 0 || focusIndex >= chunks.size()) {
            throw new IllegalArgumentException("焦点块索引越界");
        }
        var focus = chunks.get(focusIndex);
        if (focus.content().length() > characterBudget) {
            throw new IllegalArgumentException("焦点块超过抽取上下文预算，请调整分块配置");
        }
        var remaining = characterBudget - focus.content().length();
        var previous = new ArrayList<ContextChunk>();
        var next = new ArrayList<ContextChunk>();

        if (focusIndex > 0 && hasPreviousBoundaryRisk(focus.content()) && remaining > 0) {
            var source = chunks.get(focusIndex - 1);
            var length =
                    Math.min(
                            Math.min(DEFAULT_NEIGHBOR_LIMIT, remaining), source.content().length());
            previous.add(
                    new ContextChunk(
                            source.stableId(),
                            source.content().substring(source.content().length() - length),
                            length < source.content().length()));
            remaining -= length;
        }
        if (focusIndex + 1 < chunks.size()
                && hasNextBoundaryRisk(focus.content())
                && remaining > 0) {
            var source = chunks.get(focusIndex + 1);
            var length =
                    Math.min(
                            Math.min(DEFAULT_NEIGHBOR_LIMIT, remaining), source.content().length());
            next.add(
                    new ContextChunk(
                            source.stableId(),
                            source.content().substring(0, length),
                            length < source.content().length()));
        }
        return new FocusContext(
                focus.stableId(), focus.content(), List.copyOf(previous), List.copyOf(next));
    }

    private boolean hasPreviousBoundaryRisk(String content) {
        var trimmed = content.stripLeading();
        return trimmed.startsWith("其")
                || trimmed.startsWith("该")
                || trimmed.startsWith("此")
                || trimmed.startsWith("这")
                || trimmed.startsWith("它")
                || trimmed.startsWith("他们")
                || trimmed.startsWith("因此")
                || trimmed.startsWith("同时")
                || (!trimmed.isEmpty() && Character.isLowerCase(trimmed.charAt(0)));
    }

    private boolean hasNextBoundaryRisk(String content) {
        var trimmed = content.stripTrailing();
        if (trimmed.isEmpty()) {
            return false;
        }
        var last = trimmed.charAt(trimmed.length() - 1);
        return "。！？.!?；;：:".indexOf(last) < 0;
    }

    public record FocusContext(
            UUID focusChunkId,
            String focusContent,
            List<ContextChunk> previousChunks,
            List<ContextChunk> nextChunks) {
        public List<UUID> contextChunkIds() {
            return java.util.stream.Stream.concat(previousChunks.stream(), nextChunks.stream())
                    .map(ContextChunk::chunkId)
                    .toList();
        }
    }

    public record ContextChunk(UUID chunkId, String content, boolean truncated) {}
}
