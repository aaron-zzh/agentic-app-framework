/**
 * 图谱记忆仓库。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

/** 图谱记忆 Neo4j 数据访问。 */
public interface GraphMemoryRepository extends Neo4jRepository<GraphMemoryNode, String> {

    List<GraphMemoryNode> findByUserId(Long userId);

    List<GraphMemoryNode> findByUserIdAndEntityType(Long userId, String entityType);

    /** 按名称模糊搜索 */
    List<GraphMemoryNode> findByUserIdAndNameContaining(Long userId, String keyword);

    /** 多跳关系查询 */
    @Query(
            "MATCH (n:MemoryEntity {userId: $userId})-[r:RELATES_TO*1..2]-(m:MemoryEntity) "
                    + "WHERE n.name = $entityName RETURN DISTINCT m")
    List<GraphMemoryNode> findRelatedEntities(Long userId, String entityName);
}
