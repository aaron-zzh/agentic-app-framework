package com.xuejiai.aaf.module.system.role.relation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.role.relation.domain.ResourceRelation;
import com.xuejiai.aaf.module.system.role.relation.domain.ResourceRelation.RelationType;
import com.xuejiai.aaf.module.system.role.relation.domain.ResourceRelation.SubjectType;

/**
 * 资源关系仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface ResourceRelationRepository extends JpaRepository<ResourceRelation, Long> {

    /** 按资源查询所有关系 */
    List<ResourceRelation> findByResourceTypeAndResourceIdAndDeletedFalse(
            String resourceType, Long resourceId);

    /** 精确查询一条关系 */
    Optional<ResourceRelation> findByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectIdAndDeletedFalse(
            String resourceType, Long resourceId, RelationType relation,
            SubjectType subjectType, Long subjectId);

    /** 检查主体是否拥有指定关系 */
    boolean existsByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectIdAndDeletedFalse(
            String resourceType, Long resourceId, RelationType relation,
            SubjectType subjectType, Long subjectId);
}
