package com.xuejiai.aaf.module.system.org.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_MANAGER_REMOVE_FORBIDDEN;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_MANAGER_REQUIRED;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_MEMBER_ALREADY_EXISTS;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_MEMBER_NOT_FOUND;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_ORG_CONTEXT_REQUIRED;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.WORKSPACE_SLUG_EXISTS;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
import com.xuejiai.aaf.module.system.org.domain.WorkspaceMember;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.WorkspaceRepository;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceCreateDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceMemberAddDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceMemberVO;
import com.xuejiai.aaf.module.system.org.vo.WorkspacePageDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceVO;

import lombok.RequiredArgsConstructor;

/**
 * 工作区服务，继承 BaseCrudService 获得标准 CRUD 能力。
 *
 * <p>权限模型不设独立角色层级：{@code createBy} 即工作区管理者，拥有邀请/移除成员、改名、删除工作区等 全部管理权限。组织成员不自动加入工作区，需显式邀请/加入。详见设计文档
 * {@code docs/design/apps/service/workspace-isolation.md}。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService
        extends BaseCrudService<
                Workspace, WorkspaceVO, WorkspaceCreateDTO, WorkspaceUpdateDTO, WorkspacePageDTO> {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final OperatorContext operatorContext;
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "slug", "createTime");

    @Override
    protected WorkspaceRepository getRepository() {
        return workspaceRepository;
    }

    @Override
    protected WorkspaceVO toVO(Workspace w) {
        return new WorkspaceVO(
                w.getId(),
                w.getOrgId(),
                w.getName(),
                w.getSlug(),
                w.getCreateBy(),
                w.getCreateTime());
    }

    @Override
    protected Workspace toEntity(WorkspaceCreateDTO dto) {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw exception(WORKSPACE_ORG_CONTEXT_REQUIRED);
        }
        if (workspaceRepository.findByOrgIdAndSlugAndDeletedFalse(orgId, dto.slug()).isPresent()) {
            throw exception(WORKSPACE_SLUG_EXISTS);
        }
        var workspace = new Workspace();
        workspace.setOrgId(orgId);
        workspace.setName(dto.name());
        workspace.setSlug(dto.slug());
        return workspace;
    }

    @Override
    protected void updateEntity(Workspace workspace, WorkspaceUpdateDTO dto) {
        if (dto.name() != null) {
            workspace.setName(dto.name());
        }
    }

    @Override
    protected Specification<Workspace> buildSpec(WorkspacePageDTO req) {
        return SpecificationBuilder.<Workspace>builder()
                .likeIfPresent("name", req.getName())
                .build();
    }

    /** 创建工作区后，创建者自动成为该工作区成员，保证创建者不会看不到自己创建的工作区。 */
    @Override
    @Transactional
    public WorkspaceVO create(WorkspaceCreateDTO request) {
        var vo = super.create(request);
        operatorContext
                .currentOwnerId()
                .ifPresent(
                        userId -> {
                            var member = new WorkspaceMember();
                            member.setWorkspaceId(vo.id());
                            member.setUserId(userId);
                            workspaceMemberRepository.save(member);
                        });
        return vo;
    }

    /** 更新工作区前，仅允许创建者（管理者）改名。 */
    @Override
    protected void beforeUpdate(Workspace workspace, WorkspaceUpdateDTO request) {
        requireManager(workspace);
    }

    /** 删除工作区前，仅允许创建者（管理者）操作并清理成员关系。 */
    @Override
    protected void beforeDelete(Workspace workspace) {
        requireManager(workspace);
        workspaceMemberRepository
                .findByWorkspaceIdAndDeletedFalse(workspace.getId())
                .forEach(workspaceMemberRepository::delete);
    }

    // ==================== 成员管理 ====================

    public List<WorkspaceMemberVO> listMembers(Long workspaceId) {
        requireEntity(workspaceId); // 确认工作区存在且当前用户可见
        return workspaceMemberRepository.findByWorkspaceIdAndDeletedFalse(workspaceId).stream()
                .map(this::toMemberVO)
                .toList();
    }

    /** 邀请成员加入工作区，仅工作区管理者（创建者）可操作。 */
    @Transactional
    public WorkspaceMemberVO addMember(Long workspaceId, WorkspaceMemberAddDTO dto) {
        var workspace = requireEntity(workspaceId);
        requireManager(workspace);
        if (workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndDeletedFalse(
                workspaceId, dto.userId())) {
            throw exception(WORKSPACE_MEMBER_ALREADY_EXISTS);
        }
        var member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(dto.userId());
        return toMemberVO(workspaceMemberRepository.save(member));
    }

    /** 移除工作区成员，仅工作区管理者（创建者）可操作；管理者本人不可被移除。 */
    @Transactional
    public void removeMember(Long workspaceId, Long userId) {
        var workspace = requireEntity(workspaceId);
        requireManager(workspace);
        if (workspace.getCreateBy() != null && workspace.getCreateBy().equals(userId)) {
            throw exception(WORKSPACE_MANAGER_REMOVE_FORBIDDEN);
        }
        var member =
                workspaceMemberRepository
                        .findByWorkspaceIdAndUserIdAndDeletedFalse(workspaceId, userId)
                        .orElseThrow(() -> exception(WORKSPACE_MEMBER_NOT_FOUND));
        workspaceMemberRepository.delete(member);
    }

    /** 校验当前用户是否为该工作区的管理者（创建者）。 */
    private void requireManager(Workspace workspace) {
        var currentUserId = operatorContext.currentOwnerId().orElse(null);
        if (currentUserId == null
                || workspace.getCreateBy() == null
                || !workspace.getCreateBy().equals(currentUserId)) {
            throw exception(WORKSPACE_MANAGER_REQUIRED);
        }
    }

    private WorkspaceMemberVO toMemberVO(WorkspaceMember m) {
        return new WorkspaceMemberVO(
                m.getId(), m.getWorkspaceId(), m.getUserId(), m.getCreateTime());
    }
}
