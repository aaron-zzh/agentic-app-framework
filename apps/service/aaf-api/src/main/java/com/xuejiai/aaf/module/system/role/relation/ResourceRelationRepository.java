package com.xuejiai.aaf.module.system.role.relation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 资源关系仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ResourceRelationRepository extends JpaRepository<ResourceRelation, Long> {

    List<ResourceRelation> findByResourceTypeAndResourceId(String resourceType, Long resourceId);

    List<ResourceRelation> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);

    boolean existsByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectId(
            String resourceType, Long resourceId, String relation, String subjectType, Long subjectId);

    void deleteByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectId(
            String resourceType, Long resourceId, String relation, String subjectType, Long subjectId);
}
