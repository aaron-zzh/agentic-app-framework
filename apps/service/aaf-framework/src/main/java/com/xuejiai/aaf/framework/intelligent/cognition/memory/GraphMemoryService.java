/**
 * 图谱记忆服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 图谱记忆：Neo4j 实体关系存储、时序图谱、多跳查询。 */
@Service
@RequiredArgsConstructor
public class GraphMemoryService {

    private final GraphMemoryRepository repository;

    /** 存储实体节点 */
    @Transactional
    public GraphMemoryNode storeEntity(
            Long userId, String name, String entityType, String summary) {
        var node = new GraphMemoryNode();
        node.setUserId(userId);
        node.setName(name);
        node.setEntityType(entityType);
        node.setSummary(summary);
        node.setEventTime(Instant.now());
        node.setCreatedAt(Instant.now());
        return repository.save(node);
    }

    /** 添加关系 */
    @Transactional
    public void addRelation(Long sourceId, Long targetId, String relationType, Double weight) {
        var source = repository.findById(sourceId).orElseThrow();
        var target = repository.findById(targetId).orElseThrow();
        var relation = new GraphMemoryRelation();
        relation.setRelationType(relationType);
        relation.setWeight(weight != null ? weight : 1.0);
        relation.setEventTime(Instant.now());
        relation.setTarget(target);
        source.getRelations().add(relation);
        repository.save(source);
    }

    /** 查找用户的所有实体 */
    public List<GraphMemoryNode> findEntities(Long userId) {
        return repository.findByUserId(userId);
    }

    /** 多跳关系查询 */
    public List<GraphMemoryNode> findRelated(Long userId, String entityName) {
        return repository.findRelatedEntities(userId, entityName);
    }

    /** 按关键词搜索 */
    public List<GraphMemoryNode> search(Long userId, String keyword) {
        return repository.findByUserIdAndNameContaining(userId, keyword);
    }
}
