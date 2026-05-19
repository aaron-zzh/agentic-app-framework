package com.xuejiai.aaf.framework.engine.knowledge.graph;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 知识图谱服务，提供实体和关系的增删查操作
 */
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
    public void saveRelation(Long fromId, Long toId, KnowledgeRelation relation) {
        var from = entityRepository.findById(fromId).orElseThrow(
                () -> new IllegalArgumentException("源实体不存在: " + fromId));
        var to = entityRepository.findById(toId).orElseThrow(
                () -> new IllegalArgumentException("目标实体不存在: " + toId));

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

    /** 查询 N 跳邻居 */
    public List<KnowledgeEntity> findNeighbors(Long entityId, int hops) {
        // Spring Data Neo4j 使用 elementId（字符串格式）
        var entity = entityRepository.findById(entityId).orElseThrow(
                () -> new IllegalArgumentException("实体不存在: " + entityId));
        return entityRepository.findNeighbors(entity.getId().toString(), hops);
    }
}
