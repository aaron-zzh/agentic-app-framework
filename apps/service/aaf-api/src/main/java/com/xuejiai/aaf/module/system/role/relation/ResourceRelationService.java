/**
 * 资源关系管理 Service（ReBAC 层）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.relation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResourceRelationService {

    private final ResourceRelationRepository repository;

    /**
     * 授予关系
     *
     * @param dto 授权请求
     */
    @Transactional
    public void grant(GrantRelationDTO dto) {
        if (repository.existsByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectId(
                dto.resourceType(), dto.resourceId(), dto.relation(), dto.subjectType(), dto.subjectId())) {
            return; // 幂等
        }
        var entity = new ResourceRelation();
        entity.setResourceType(dto.resourceType());
        entity.setResourceId(dto.resourceId());
        entity.setRelation(dto.relation());
        entity.setSubjectType(dto.subjectType());
        entity.setSubjectId(dto.subjectId());
        repository.save(entity);
    }

    /**
     * 撤销关系
     *
     * @param dto 撤销请求
     */
    @Transactional
    public void revoke(GrantRelationDTO dto) {
        repository.deleteByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectId(
                dto.resourceType(), dto.resourceId(), dto.relation(), dto.subjectType(), dto.subjectId());
    }

    /**
     * 检查是否拥有关系
     *
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param relation 关系类型
     * @param subjectType 主体类型
     * @param subjectId 主体 ID
     * @return 是否拥有
     */
    public boolean check(String resourceType, Long resourceId, String relation, String subjectType, Long subjectId) {
        return repository.existsByResourceTypeAndResourceIdAndRelationAndSubjectTypeAndSubjectId(
                resourceType, resourceId, relation, subjectType, subjectId);
    }

    /**
     * 查询资源的所有关系
     *
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @return 关系列表
     */
    public List<ResourceRelationVO> listByResource(String resourceType, Long resourceId) {
        return repository.findByResourceTypeAndResourceId(resourceType, resourceId).stream()
                .map(e -> new ResourceRelationVO(e.getId(), e.getResourceType(), e.getResourceId(),
                        e.getRelation(), e.getSubjectType(), e.getSubjectId()))
                .toList();
    }
}
