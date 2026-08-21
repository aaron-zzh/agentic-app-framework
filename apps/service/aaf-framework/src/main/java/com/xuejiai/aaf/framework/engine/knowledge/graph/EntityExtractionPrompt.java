package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.Set;

/** 焦点块事实抽取固定输出契约。 */
public final class EntityExtractionPrompt {

    public static final String OUTPUT_CONTRACT_VERSION = "knowledge-fact-schema-v2";
    public static final int MAX_FACTS_PER_CHUNK = 100;
    public static final int MAX_RESPONSE_LENGTH = 1_000_000;
    public static final int MAX_NAME_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;
    public static final int MAX_EVIDENCE_LENGTH = 4_000;
    public static final int MAX_ATTRIBUTES_LENGTH = 16_384;
    public static final int MAX_ATTRIBUTE_COUNT = 64;

    public static final Set<String> REQUIRED_FIELDS =
            Set.of(
                    "subject",
                    "subjectType",
                    "subjectDesc",
                    "predicate",
                    "object",
                    "objectKind",
                    "objectType",
                    "objectDesc",
                    "evidenceQuote",
                    "startOffset",
                    "endOffset",
                    "confidence");

    public static final Set<String> OPTIONAL_FIELDS = Set.of("validAt", "invalidAt", "attributes");

    public static final Set<String> RESERVED_ATTRIBUTE_KEYS =
            Set.of(
                    "assertionId",
                    "factId",
                    "evidenceId",
                    "validAt",
                    "invalidAt",
                    "recordedAt",
                    "expiredAt",
                    "expirationReason",
                    "referenceTime",
                    "knowledgeBaseId",
                    "documentId",
                    "sourceId",
                    "runId");

    public static final Set<String> ENTITY_TYPES =
            Set.of(
                    "PERSON",
                    "ORGANIZATION",
                    "LOCATION",
                    "EVENT",
                    "PRODUCT",
                    "TECHNOLOGY",
                    "SYSTEM",
                    "DOCUMENT",
                    "CONCEPT",
                    "METRIC",
                    "ROLE",
                    "OTHER");

    private EntityExtractionPrompt() {}
}
