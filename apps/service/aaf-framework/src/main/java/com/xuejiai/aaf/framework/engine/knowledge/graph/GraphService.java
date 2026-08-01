package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 知识图谱服务，提供实体和关系的增删查操作 */
@Service
@RequiredArgsConstructor
public class GraphService {

    private final KnowledgeEntityRepository entityRepository;

    /** 保存实体节点 */
    public KnowledgeEntity saveEntity(KnowledgeEntity entity) {
        var now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        return entityRepository.save(entity);
    }

    /** 保存关系：从 fromId 到 toId 建立 RELATES_TO 关系 */
    public void saveRelation(String fromId, String toId, KnowledgeRelation relation) {
        var from =
                entityRepository
                        .findById(fromId)
                        .orElseThrow(() -> new IllegalArgumentException("源实体不存在: " + fromId));
        var to =
                entityRepository
                        .findById(toId)
                        .orElseThrow(() -> new IllegalArgumentException("目标实体不存在: " + toId));

        relation.setTarget(to);
        if (relation.getCreatedAt() == null) {
            relation.setCreatedAt(Instant.now());
        }
        from.getRelations().add(relation);
        entityRepository.save(from);
    }

    /** 按知识库查询所有实体 */
    public List<KnowledgeEntity> findEntitiesByKnowledgeBase(Long knowledgeBaseId) {
        return entityRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    /** 按知识库查询完整节点与关系快照。 */
    public GraphSnapshot snapshot(Long knowledgeBaseId) {
        var entities = entityRepository.findGraphByKnowledgeBaseId(knowledgeBaseId);
        var nodes =
                entities.stream()
                        .map(
                                entity ->
                                        new GraphNode(
                                                entity.getId(),
                                                entity.getName(),
                                                entity.getType(),
                                                entity.getDescription(),
                                                entity.getSourceDocumentId()))
                        .toList();
        var edges =
                entities.stream()
                        .flatMap(
                                source ->
                                        source.getRelations().stream()
                                                .filter(relation -> relation.getTarget() != null)
                                                .filter(
                                                        relation ->
                                                                knowledgeBaseId.equals(
                                                                        relation.getTarget()
                                                                                .getKnowledgeBaseId()))
                                                .map(
                                                        relation ->
                                                                new GraphEdge(
                                                                        relationId(
                                                                                source, relation),
                                                                        source.getId(),
                                                                        relation.getTarget()
                                                                                .getId(),
                                                                        relation.getType(),
                                                                        relation.getConfidence(),
                                                                        relation
                                                                                .getSourceDocumentId())))
                        .toList();
        return new GraphSnapshot(nodes, edges);
    }

    /** 清理指定文档生成的关系和孤立实体。 */
    public void clearDocumentData(Long knowledgeBaseId, Long documentId) {
        entityRepository.deleteDocumentRelations(knowledgeBaseId, documentId);
        entityRepository.deleteExtractedOrphanEntities(knowledgeBaseId);
    }

    /**
     * 合并重复实体：把 {@code duplicateIds} 的所有出边/入边转移到 {@code keepId}，再删除 {@code duplicateIds} 节点。
     *
     * <p>供 {@code EntityResolutionService} 异步消歧使用。合并后不保留 duplicate 侧的关系记录——
     * 图谱只关心"当前哪些实体、有哪些关系"，历史归属通过关系上的 {@code sourceDocumentId} 追溯， 不需要额外记录"哪个实体被合并进了谁"。
     */
    public void mergeEntities(String keepId, List<String> duplicateIds) {
        if (duplicateIds == null || duplicateIds.isEmpty()) {
            return;
        }
        entityRepository.mergeEntities(keepId, duplicateIds);
    }

    /** 查询 N 跳邻居。 */
    public List<KnowledgeEntity> findNeighbors(String entityId, int hops) {
        return entityRepository.findNeighbors(entityId, hops);
    }

    private String relationId(KnowledgeEntity source, KnowledgeRelation relation) {
        if (relation.getId() != null && !relation.getId().isBlank()) {
            return relation.getId();
        }
        return "%s:%s:%s:%s"
                .formatted(
                        source.getId(),
                        relation.getTarget().getId(),
                        java.util.Objects.toString(relation.getType(), ""),
                        java.util.Objects.toString(relation.getSourceDocumentId(), ""));
    }

    public record GraphSnapshot(List<GraphNode> nodes, List<GraphEdge> edges) {}

    public record GraphNode(
            String id, String name, String type, String description, Long sourceDocumentId) {}

    public record GraphEdge(
            String id,
            String sourceId,
            String targetId,
            String type,
            Double confidence,
            Long sourceDocumentId) {}
}
