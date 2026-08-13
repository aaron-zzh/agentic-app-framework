package com.xuejiai.aaf.module.system.org.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.org.domain.Workspace;
import com.xuejiai.aaf.module.system.org.service.WorkspaceService;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceCreateDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceMemberAddDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceMemberVO;
import com.xuejiai.aaf.module.system.org.vo.WorkspacePageDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.WorkspaceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 工作区管理接口，继承 BaseCrudController 获得标准 CRUD 接口。
 *
 * <p>权限模型不设独立角色层级：工作区 {@code ownerId} 指向的用户拥有全部管理权限（邀请/移除成员、改名、删除工作区）， 由 {@link WorkspaceService} 内部校验，
 * 不使用 {@code @PreAuthorize} 角色声明。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "工作区管理")
@RestController
@RequestMapping("/api/system/workspaces")
@RequiredArgsConstructor
public class WorkspaceController
        extends BaseCrudController<
                Workspace, WorkspaceVO, WorkspaceCreateDTO, WorkspaceUpdateDTO, WorkspacePageDTO> {

    private final WorkspaceService workspaceService;

    @Override
    protected BaseCrudService<
                    Workspace,
                    WorkspaceVO,
                    WorkspaceCreateDTO,
                    WorkspaceUpdateDTO,
                    WorkspacePageDTO>
            getService() {
        return workspaceService;
    }

    // ==================== 成员管理 ====================

    @Operation(summary = "获取工作区成员列表")
    @GetMapping("/{workspaceId}/members")
    public Result<List<WorkspaceMemberVO>> listMembers(@PathVariable Long workspaceId) {
        return Result.success(workspaceService.listMembers(workspaceId));
    }

    @Operation(summary = "邀请成员加入工作区", description = "仅工作区管理者可操作")
    @PostMapping("/{workspaceId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<WorkspaceMemberVO> addMember(
            @PathVariable Long workspaceId, @Validated @RequestBody WorkspaceMemberAddDTO request) {
        return Result.success(workspaceService.addMember(workspaceId, request));
    }

    @Operation(summary = "移除工作区成员", description = "仅工作区管理者可操作，管理者本人不可被移除")
    @DeleteMapping("/{workspaceId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long workspaceId, @PathVariable Long userId) {
        workspaceService.removeMember(workspaceId, userId);
        return Result.success();
    }
}
