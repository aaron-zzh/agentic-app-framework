package com.xuejiai.aaf.autodev.doc.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import com.xuejiai.aaf.autodev.doc.domain.AutodevDocNode;

public interface AutodevDocRelationRepository extends Neo4jRepository<AutodevDocNode, Long> {

    Optional<AutodevDocNode> findByDocId(Long docId);

    @Query("MATCH (a:AutodevDoc {docId: $docId})-[r:REFERENCES]->(b:AutodevDoc) RETURN b")
    List<AutodevDocNode> findOutgoing(Long docId);

    @Query("MATCH (a:AutodevDoc)-[r:REFERENCES]->(b:AutodevDoc {docId: $docId}) RETURN a")
    List<AutodevDocNode> findIncoming(Long docId);

    @Query(
            "MATCH (a:AutodevDoc {docId: $sourceId}), (b:AutodevDoc {docId: $targetId}) "
                    + "MERGE (a)-[:REFERENCES {linkType: $linkType}]->(b)")
    void mergeRelation(Long sourceId, Long targetId, String linkType);
}
