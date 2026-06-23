package com.xuejiai.aaf.module.ai.aigc.project.resource.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.resource.domain.UserProjectResource;
import com.xuejiai.aaf.module.ai.aigc.project.resource.repository.UserProjectResourceRepository;
import com.xuejiai.aaf.module.ai.aigc.project.resource.vo.UserProjectResourceLinkDTO;
import com.xuejiai.aaf.module.ai.aigc.project.resource.vo.UserProjectResourceVO;

import lombok.RequiredArgsConstructor;

/** 项目-资源关联服务，所有操作前校验 project ownership。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProjectResourceService {

    private final UserProjectResourceRepository resourceRepository;
    private final AigcProjectRepository projectRepository;
    private final OperatorContext operatorContext;

    public List<UserProjectResourceVO> list(Long projectId) {
        requireOwnership(projectId);
        return resourceRepository.findByProjectIdAndDeletedFalseOrderBySortOrder(projectId).stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public UserProjectResourceVO link(Long projectId, UserProjectResourceLinkDTO dto) {
        requireOwnership(projectId);
        // 幂等：已存在则直接返回
        if (resourceRepository.existsByProjectIdAndResourceTypeAndResourceIdAndDeletedFalse(
                projectId, dto.resourceType(), dto.resourceId())) {
            return resourceRepository
                    .findByProjectIdAndDeletedFalseOrderBySortOrder(projectId)
                    .stream()
                    .filter(
                            r ->
                                    r.getResourceType().equals(dto.resourceType())
                                            && r.getResourceId().equals(dto.resourceId()))
                    .findFirst()
                    .map(this::toVO)
                    .orElseThrow();
        }
        var entity = new UserProjectResource();
        entity.setProjectId(projectId);
        entity.setResourceType(dto.resourceType());
        entity.setResourceId(dto.resourceId());
        entity.setRole(dto.role());
        entity.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        return toVO(resourceRepository.save(entity));
    }

    @Transactional
    public void unlink(Long projectId, Long id) {
        requireOwnership(projectId);
        var entity =
                resourceRepository
                        .findById(id)
                        .filter(r -> r.getProjectId().equals(projectId))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "资源关联不存在"));
        resourceRepository.delete(entity);
    }

    /** 内部方法：fork 时批量写入资源关联，无权限校验（由上层 fork 事务保证）。 */
    @Transactional
    public void linkBatch(Long projectId, List<UserProjectResourceLinkDTO> links) {
        for (var dto : links) {
            link(projectId, dto);
        }
    }

    // ---- 私有辅助 ----

    private AigcProject requireOwnership(Long projectId) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在"));
        if (!project.getUserId().equals(userId)) {
            // 404 防探测，不暴露资源存在性
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private UserProjectResourceVO toVO(UserProjectResource e) {
        var vo = new UserProjectResourceVO();
        vo.setId(e.getId());
        vo.setProjectId(e.getProjectId());
        vo.setResourceType(e.getResourceType());
        vo.setResourceId(e.getResourceId());
        vo.setRole(e.getRole());
        vo.setSortOrder(e.getSortOrder());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
