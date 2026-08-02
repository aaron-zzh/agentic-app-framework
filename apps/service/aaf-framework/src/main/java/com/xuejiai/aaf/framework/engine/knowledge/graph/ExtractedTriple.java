package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** LLM 从单个焦点块抽取的事实候选，证据只能引用焦点块。 */
public record ExtractedTriple(
        String subject,
        @JsonProperty("subjectType") String subjectType,
        @JsonProperty("subjectDesc") String subjectDescription,
        String predicate,
        String object,
        @JsonProperty("objectKind") String objectKind,
        @JsonProperty("objectType") String objectType,
        @JsonProperty("objectDesc") String objectDescription,
        String evidenceQuote,
        Integer startOffset,
        Integer endOffset,
        Double confidence,
        Instant validAt,
        Instant invalidAt,
        Map<String, Object> attributes) {

    public ExtractedTriple {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
