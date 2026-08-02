package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.neo4j.driver.Driver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.FactProjection;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.OutboxEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Neo4j 只读加速投影；事实修改必须先写 PostgreSQL。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphProjectionService {

    private final Driver driver;
    private final TrustedKnowledgeStore store;

    @Scheduled(fixedDelayString = "${aaf.knowledge.graph.outbox-delay-ms:5000}")
    public void projectPending() {
        for (var event : store.claimOutbox(100)) {
            try {
                project(event);
                store.completeOutbox(event);
            } catch (RuntimeException failure) {
                store.failOutbox(event, failure.getMessage());
                log.warn("知识图投影失败，eventId={}", event.eventId(), failure);
            }
        }
    }

    /** 直接命中事实优先；有剩余额度时扩展其端点的相邻事实，形成最多二跳的证据候选。 */
    public Set<String> searchFactKeys(
            String query, Set<UUID> authorizedKnowledgeBaseIds, SourceFilters filters, int limit) {
        if (authorizedKnowledgeBaseIds.isEmpty() || limit <= 0) {
            return Set.of();
        }
        var directCypher =
                """
                MATCH (subject:KnowledgeEntity)-[fact:FACT]->(object:KnowledgeEntity)
                WHERE subject.knowledgeBaseId IN $knowledgeBaseIds
                  AND object.knowledgeBaseId IN $knowledgeBaseIds
                  AND (size($sourceTypes) = 0 OR any(value IN fact.sourceTypes WHERE value IN $sourceTypes))
                  AND (size($sourceKeys) = 0 OR any(value IN fact.sourceKeys WHERE value IN $sourceKeys))
                  AND (size($documentIds) = 0 OR any(value IN fact.documentIds WHERE value IN $documentIds))
                  AND (toLower(subject.name) CONTAINS toLower($query)
                       OR toLower(object.name) CONTAINS toLower($query)
                       OR toLower(fact.predicate) CONTAINS toLower($query))
                RETURN DISTINCT fact.factKey AS factKey
                ORDER BY factKey
                LIMIT $limit
                """;
        var knowledgeBaseIds =
                authorizedKnowledgeBaseIds.stream().map(UUID::toString).sorted().toList();
        var sourceTypes = filters.sourceTypes().stream().sorted().toList();
        var sourceKeys = filters.sourceKeys().stream().sorted().toList();
        var documentIds = filters.documentIds().stream().map(UUID::toString).sorted().toList();
        try (var session = driver.session()) {
            var directFactKeys =
                    session.run(
                                    directCypher,
                                    Map.of(
                                            "knowledgeBaseIds",
                                            knowledgeBaseIds,
                                            "query",
                                            query,
                                            "sourceTypes",
                                            sourceTypes,
                                            "sourceKeys",
                                            sourceKeys,
                                            "documentIds",
                                            documentIds,
                                            "limit",
                                            limit))
                            .list(record -> record.get("factKey").asString());
            if (directFactKeys.isEmpty() || directFactKeys.size() >= limit) {
                return Set.copyOf(directFactKeys);
            }

            var relatedCypher =
                    """
                    MATCH (source:KnowledgeEntity)-[direct:FACT]->(target:KnowledgeEntity)
                    WHERE direct.factKey IN $directFactKeys
                      AND source.knowledgeBaseId IN $knowledgeBaseIds
                      AND target.knowledgeBaseId IN $knowledgeBaseIds
                    WITH collect(DISTINCT source) + collect(DISTINCT target) AS anchors
                    UNWIND anchors AS anchor
                    MATCH (anchor)-[related:FACT]-(neighbor:KnowledgeEntity)
                    WHERE anchor.knowledgeBaseId IN $knowledgeBaseIds
                      AND neighbor.knowledgeBaseId IN $knowledgeBaseIds
                      AND NOT (related.factKey IN $directFactKeys)
                      AND (size($sourceTypes) = 0 OR any(value IN related.sourceTypes WHERE value IN $sourceTypes))
                      AND (size($sourceKeys) = 0 OR any(value IN related.sourceKeys WHERE value IN $sourceKeys))
                      AND (size($documentIds) = 0 OR any(value IN related.documentIds WHERE value IN $documentIds))
                    RETURN DISTINCT related.factKey AS factKey
                    ORDER BY factKey
                    LIMIT $limit
                    """;
            var remaining = limit - directFactKeys.size();
            var relatedFactKeys =
                    session.run(
                                    relatedCypher,
                                    Map.of(
                                            "knowledgeBaseIds",
                                            knowledgeBaseIds,
                                            "directFactKeys",
                                            directFactKeys,
                                            "sourceTypes",
                                            sourceTypes,
                                            "sourceKeys",
                                            sourceKeys,
                                            "documentIds",
                                            documentIds,
                                            "limit",
                                            remaining))
                            .list(record -> record.get("factKey").asString());
            var result = new LinkedHashSet<String>(directFactKeys.size() + relatedFactKeys.size());
            result.addAll(directFactKeys);
            result.addAll(relatedFactKeys);
            return Set.copyOf(result);
        }
    }

    public void rebuild(UUID knowledgeBaseId, String requestKey) {
        var rebuild = store.beginGraphRebuild(knowledgeBaseId, requestKey);
        if (!rebuild.execute()) {
            return;
        }
        try {
            try (var session = driver.session()) {
                session.run(
                                "MATCH (entity:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId}) DETACH DELETE entity",
                                Map.of("knowledgeBaseId", knowledgeBaseId.toString()))
                        .consume();
            }
            for (var factId : store.currentFactIds(knowledgeBaseId)) {
                store.currentFactProjection(factId).ifPresent(this::projectFact);
            }
            var expectedFacts = Set.copyOf(store.currentFactIds(knowledgeBaseId));
            var expectedEntities = store.currentEntityIds(knowledgeBaseId);
            var actualFacts = projectedFactIds(knowledgeBaseId);
            var actualEntities = projectedEntityIds(knowledgeBaseId);
            var matched =
                    expectedFacts.equals(actualFacts) && expectedEntities.equals(actualEntities);
            if (!store.completeGraphRebuild(
                    rebuild, matched, matched ? null : "Neo4j 与 PostgreSQL 当前实体/事实集合不一致")) {
                log.warn(
                        "知识图投影重建未收敛，knowledgeBaseId={}, requestKey={}",
                        knowledgeBaseId,
                        requestKey);
                return;
            }
            log.info("知识图投影重建完成，knowledgeBaseId={}, requestKey={}", knowledgeBaseId, requestKey);
        } catch (RuntimeException failure) {
            store.completeGraphRebuild(rebuild, false, failure.getMessage());
            throw failure;
        }
    }

    private Set<UUID> projectedFactIds(UUID knowledgeBaseId) {
        try (var session = driver.session()) {
            return Set.copyOf(
                    session.run(
                                    """
                                    MATCH (:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                          -[fact:FACT]->
                                          (:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                    RETURN DISTINCT fact.id AS id ORDER BY id
                                    """,
                                    Map.of("knowledgeBaseId", knowledgeBaseId.toString()))
                            .list(record -> UUID.fromString(record.get("id").asString())));
        }
    }

    private Set<UUID> projectedEntityIds(UUID knowledgeBaseId) {
        try (var session = driver.session()) {
            return Set.copyOf(
                    session.run(
                                    """
                                    MATCH (entity:KnowledgeEntity {knowledgeBaseId: $knowledgeBaseId})
                                    RETURN DISTINCT entity.id AS id ORDER BY id
                                    """,
                                    Map.of("knowledgeBaseId", knowledgeBaseId.toString()))
                            .list(record -> UUID.fromString(record.get("id").asString())));
        }
    }

    private void project(OutboxEvent event) {
        switch (event.eventType()) {
            case "FACT_UPSERT" ->
                    store.currentFactProjection(event.aggregateId()).ifPresent(this::projectFact);
            case "DOCUMENT_REVOKED" -> reconcileRevokedRun(event);
            default -> throw new IllegalArgumentException("未知知识图投影事件: " + event.eventType());
        }
    }

    private void projectFact(FactProjection fact) {
        var cypher =
                """
                MERGE (subject:KnowledgeEntity {id: $subjectId})
                SET subject.knowledgeBaseId = $knowledgeBaseId,
                    subject.name = $subjectName,
                    subject.type = $subjectType,
                    subject.description = $subjectDescription
                MERGE (object:KnowledgeEntity {id: $objectId})
                SET object.knowledgeBaseId = $knowledgeBaseId,
                    object.name = $objectName,
                    object.type = $objectType,
                    object.description = $objectDescription
                MERGE (subject)-[relation:FACT {factKey: $factKey}]->(object)
                SET relation.id = $factId,
                    relation.predicate = $predicate,
                    relation.confidence = $confidence,
                    relation.evidenceIds = $evidenceIds,
                    relation.sourceTypes = $sourceTypes,
                    relation.sourceKeys = $sourceKeys,
                    relation.documentIds = $documentIds
                """;
        try (var session = driver.session()) {
            session.run(
                            cypher,
                            Map.ofEntries(
                                    Map.entry("subjectId", fact.subject().id().toString()),
                                    Map.entry("objectId", fact.object().id().toString()),
                                    Map.entry("knowledgeBaseId", fact.knowledgeBaseId().toString()),
                                    Map.entry("subjectName", fact.subject().name()),
                                    Map.entry("subjectType", fact.subject().type()),
                                    Map.entry(
                                            "subjectDescription",
                                            text(fact.subject().description())),
                                    Map.entry("objectName", fact.object().name()),
                                    Map.entry("objectType", fact.object().type()),
                                    Map.entry(
                                            "objectDescription", text(fact.object().description())),
                                    Map.entry("factKey", fact.factKey()),
                                    Map.entry("factId", fact.factId().toString()),
                                    Map.entry("predicate", fact.predicate()),
                                    Map.entry("confidence", fact.confidence()),
                                    Map.entry(
                                            "evidenceIds",
                                            fact.evidenceIds().stream()
                                                    .map(UUID::toString)
                                                    .sorted()
                                                    .toList()),
                                    Map.entry(
                                            "sourceTypes",
                                            fact.sourceTypes().stream().sorted().toList()),
                                    Map.entry(
                                            "sourceKeys",
                                            fact.sourceKeys().stream().sorted().toList()),
                                    Map.entry(
                                            "documentIds",
                                            fact.documentIds().stream()
                                                    .map(UUID::toString)
                                                    .sorted()
                                                    .toList())))
                    .consume();
        }
    }

    private void deleteRun(UUID runId) {
        if (runId == null) {
            return;
        }
        var activeFactIds = store.currentFactIdsForRunExclusion(runId);
        try (var session = driver.session()) {
            session.run(
                            """
                            MATCH ()-[relation:FACT]->()
                            WHERE relation.id IN $factIds
                            DELETE relation
                            """,
                            Map.of("factIds", activeFactIds.stream().map(UUID::toString).toList()))
                    .consume();
            session.run("MATCH (entity:KnowledgeEntity) WHERE NOT (entity)--() DELETE entity")
                    .consume();
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
