package com.xuejiai.aaf.module.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import com.xuejiai.aaf.module.document.domain.DocNode;

public interface DocRelationRepository extends Neo4jRepository<DocNode, Long> {

    Optional<DocNode> findByDocId(Long docId);

    /** 查询直接引用关系（出方向） */
    @Query("MATCH (a:DocNode {docId: $docId})-[r:REFERENCES]->(b:DocNode) RETURN b")
    List<DocNode> findOutgoing(Long docId);

    /** 查询直接引用关系（入方向） */
    @Query("MATCH (a:DocNode)-[r:REFERENCES]->(b:DocNode {docId: $docId}) RETURN a")
    List<DocNode> findIncoming(Long docId);

    /** 创建或合并引用关系 */
    @Query(
            "MATCH (a:DocNode {docId: $sourceId}), (b:DocNode {docId: $targetId}) "
                    + "MERGE (a)-[:REFERENCES {linkType: $linkType}]->(b)")
    void mergeRelation(Long sourceId, Long targetId, String linkType);
}
