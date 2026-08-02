package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.security.authorization.AuthorizationSubject;

/** NexusKB 授权检索合同。 */
public final class KnowledgeSearchContracts {

    private KnowledgeSearchContracts() {}

    public static Map<UUID, Double> validateKnowledgeBaseWeights(Map<UUID, Double> weights) {
        if (weights == null) {
            return Map.of();
        }
        for (var entry : weights.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("知识库权重 key 不能为空");
            }
            var value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("知识库权重必须为有限非负数");
            }
        }
        return Map.copyOf(weights);
    }

    public enum Visibility {
        PRIVATE,
        ORG,
        SYSTEM_PUBLIC
    }

    public enum Channel {
        VECTOR,
        KEYWORD,
        GRAPH
    }

    /** 三路检索共享的来源过滤，未知字段失败关闭，避免各分支解释不一致。 */
    public record SourceFilters(
            Set<String> sourceTypes, Set<String> sourceKeys, Set<UUID> documentIds) {

        private static final Set<String> SUPPORTED_FIELDS =
                Set.of("sourceTypes", "sourceKeys", "documentIds");
        private static final Set<String> SUPPORTED_SOURCE_TYPES =
                Set.of("FILE", "URL", "BUSINESS_OBJECT", "SYSTEM");

        public SourceFilters {
            sourceTypes = normalizeSourceTypes(sourceTypes);
            sourceKeys = normalizeSourceKeys(sourceKeys);
            documentIds = documentIds == null ? Set.of() : Set.copyOf(documentIds);
        }

        public static SourceFilters from(Map<String, Object> values) {
            if (values == null || values.isEmpty()) {
                return new SourceFilters(Set.of(), Set.of(), Set.of());
            }
            if (!SUPPORTED_FIELDS.containsAll(values.keySet())) {
                throw new IllegalArgumentException("存在不支持的知识来源过滤字段");
            }
            return new SourceFilters(
                    strictStrings(values.get("sourceTypes"), true),
                    strictStrings(values.get("sourceKeys"), false),
                    strictUuids(values.get("documentIds")));
        }

        public Map<String, Object> toMap() {
            if (sourceTypes.isEmpty() && sourceKeys.isEmpty() && documentIds.isEmpty()) {
                return Map.of();
            }
            var normalized = new java.util.LinkedHashMap<String, Object>();
            if (!sourceTypes.isEmpty()) {
                normalized.put("sourceTypes", sourceTypes.stream().sorted().toList());
            }
            if (!sourceKeys.isEmpty()) {
                normalized.put("sourceKeys", sourceKeys.stream().sorted().toList());
            }
            if (!documentIds.isEmpty()) {
                normalized.put(
                        "documentIds", documentIds.stream().map(UUID::toString).sorted().toList());
            }
            return Map.copyOf(normalized);
        }

        private static Set<String> strictStrings(Object value, boolean sourceType) {
            if (value == null) {
                return Set.of();
            }
            if (!(value instanceof Iterable<?> items)) {
                throw new IllegalArgumentException("来源过滤值必须是集合");
            }
            var result = new java.util.LinkedHashSet<String>();
            for (var item : items) {
                if (!(item instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("来源过滤集合只能包含非空字符串");
                }
                result.add(
                        sourceType ? text.trim().toUpperCase(java.util.Locale.ROOT) : text.trim());
            }
            return Set.copyOf(result);
        }

        private static Set<UUID> strictUuids(Object value) {
            if (value == null) {
                return Set.of();
            }
            if (!(value instanceof Iterable<?> items)) {
                throw new IllegalArgumentException("documentIds 必须是集合");
            }
            var result = new java.util.LinkedHashSet<UUID>();
            for (var item : items) {
                if (item instanceof UUID uuid) {
                    result.add(uuid);
                } else if (item instanceof String text && !text.isBlank()) {
                    result.add(UUID.fromString(text.trim()));
                } else {
                    throw new IllegalArgumentException("documentIds 只能包含 UUID");
                }
            }
            return Set.copyOf(result);
        }

        private static Set<String> normalizeSourceTypes(Set<String> values) {
            var normalized =
                    values == null
                            ? Set.<String>of()
                            : values.stream()
                                    .map(String::trim)
                                    .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!SUPPORTED_SOURCE_TYPES.containsAll(normalized)) {
                throw new IllegalArgumentException("存在不支持的知识来源类型");
            }
            return normalized;
        }

        private static Set<String> normalizeSourceKeys(Set<String> values) {
            if (values == null) {
                return Set.of();
            }
            var normalized = new java.util.LinkedHashSet<String>();
            for (var value : values) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("sourceKeys 不能包含空值");
                }
                normalized.add(value.trim());
            }
            return Set.copyOf(normalized);
        }
    }

    public record ChannelWeights(double vector, double keyword, double graph) {
        public ChannelWeights {
            if (vector < 0 || keyword < 0 || graph < 0 || vector + keyword + graph <= 0) {
                throw new IllegalArgumentException("检索通道权重必须非负且至少一个大于零");
            }
        }

        public static ChannelWeights defaults() {
            return new ChannelWeights(0.5, 0.3, 0.2);
        }

        public double weight(Channel channel) {
            return switch (channel) {
                case VECTOR -> vector;
                case KEYWORD -> keyword;
                case GRAPH -> graph;
            };
        }
    }

    public record AuthorizedQuery(
            AuthorizationSubject subject,
            String query,
            Set<UUID> requestedKnowledgeBaseIds,
            boolean includePublic,
            Map<UUID, Double> knowledgeBaseWeights,
            ChannelWeights channelWeights,
            int topK,
            double vectorThreshold,
            Map<String, Object> sourceFilters) {
        public AuthorizedQuery {
            if (subject == null) {
                throw new IllegalArgumentException("授权主体不能为空");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("检索文本不能为空");
            }
            requestedKnowledgeBaseIds =
                    requestedKnowledgeBaseIds == null
                            ? Set.of()
                            : Set.copyOf(requestedKnowledgeBaseIds);
            knowledgeBaseWeights = validateKnowledgeBaseWeights(knowledgeBaseWeights);
            channelWeights = channelWeights == null ? ChannelWeights.defaults() : channelWeights;
            sourceFilters = sourceFilters == null ? Map.of() : Map.copyOf(sourceFilters);
            if (topK <= 0) {
                throw new IllegalArgumentException("topK 必须大于零");
            }
            if (vectorThreshold < 0 || vectorThreshold > 1) {
                throw new IllegalArgumentException("vectorThreshold 必须在 0 到 1 之间");
            }
        }
    }

    public record AuthorizedScope(
            Map<UUID, AuthorizedKnowledgeBase> knowledgeBases,
            String authorizationSnapshotVersion) {
        public AuthorizedScope {
            knowledgeBases = knowledgeBases == null ? Map.of() : Map.copyOf(knowledgeBases);
        }
    }

    public record AuthorizedKnowledgeBase(
            UUID knowledgeBaseId, Visibility visibility, double defaultWeight) {
        public AuthorizedKnowledgeBase {
            if (defaultWeight < 0) {
                throw new IllegalArgumentException("知识库权重不能为负数");
            }
        }
    }

    public record Response(
            List<Hit> hits, Set<UUID> searchedKnowledgeBaseIds, Set<Channel> degradedChannels) {
        public Response {
            hits = hits == null ? List.of() : List.copyOf(hits);
            searchedKnowledgeBaseIds =
                    searchedKnowledgeBaseIds == null
                            ? Set.of()
                            : Set.copyOf(searchedKnowledgeBaseIds);
            degradedChannels = degradedChannels == null ? Set.of() : Set.copyOf(degradedChannels);
        }
    }

    public record Hit(
            String candidateKey,
            String content,
            double score,
            Set<Channel> matchedChannels,
            SourceRef source) {
        public Hit {
            matchedChannels = matchedChannels == null ? Set.of() : Set.copyOf(matchedChannels);
        }
    }

    public record SourceRef(
            UUID knowledgeBaseId,
            String knowledgeBaseName,
            Visibility visibility,
            UUID documentId,
            String sourceType,
            String sourceKey,
            String sourceUri,
            UUID runId,
            UUID focusChunkId,
            Set<UUID> factIds,
            Set<UUID> evidenceIds) {
        public SourceRef {
            factIds = factIds == null ? Set.of() : Set.copyOf(factIds);
            evidenceIds = evidenceIds == null ? Set.of() : Set.copyOf(evidenceIds);
        }
    }
}
