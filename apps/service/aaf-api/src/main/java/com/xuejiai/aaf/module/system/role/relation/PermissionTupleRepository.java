package com.xuejiai.aaf.module.system.role.relation;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** ReBAC 关系元组仓储。 */
public interface PermissionTupleRepository extends JpaRepository<PermissionTuple, Long> {

    List<PermissionTuple> findByObjectTypeAndObjectId(String objectType, String objectId);

    boolean existsByObjectTypeAndObjectIdAndRelationAndSubjectTypeAndSubjectIdAndSubjectRelation(
            String objectType,
            String objectId,
            String relation,
            String subjectType,
            String subjectId,
            String subjectRelation);

    void deleteByObjectTypeAndObjectIdAndRelationAndSubjectTypeAndSubjectIdAndSubjectRelation(
            String objectType,
            String objectId,
            String relation,
            String subjectType,
            String subjectId,
            String subjectRelation);

    /**
     * PG 递归 CTE：从对象反向展开 subject_relation，查找是否能到达用户或用户角色。
     */
    @Query(
            value =
                    """
                    WITH RECURSIVE relation_path AS (
                        SELECT t.object_type, t.object_id, t.relation,
                               t.subject_type, t.subject_id, t.subject_relation, 1 AS depth
                        FROM permission_tuple t
                        WHERE t.object_type = :objectType
                          AND t.object_id = :objectId
                          AND t.relation IN (:relations)
                          AND t.deleted = FALSE
                          AND (t.expires_at IS NULL OR t.expires_at > :now)
                        UNION ALL
                        SELECT t.object_type, t.object_id, t.relation,
                               t.subject_type, t.subject_id, t.subject_relation, p.depth + 1
                        FROM permission_tuple t
                        JOIN relation_path p
                          ON p.subject_relation <> ''
                         AND t.object_type = p.subject_type
                         AND t.object_id = p.subject_id
                         AND t.relation = p.subject_relation
                        WHERE p.depth < :maxDepth
                          AND t.deleted = FALSE
                          AND (t.expires_at IS NULL OR t.expires_at > :now)
                    )
                    SELECT EXISTS (
                        SELECT 1 FROM relation_path p
                        WHERE (p.subject_type = 'USER' AND p.subject_id = :userId)
                           OR (p.subject_type = 'ROLE' AND p.subject_id IN (:roleIds))
                    )
                    """,
            nativeQuery = true)
    boolean hasPath(
            String objectType,
            String objectId,
            List<String> relations,
            String userId,
            List<String> roleIds,
            int maxDepth,
            Instant now);
}
