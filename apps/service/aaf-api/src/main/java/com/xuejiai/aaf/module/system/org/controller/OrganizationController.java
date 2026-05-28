package com.xuejiai.aaf.module.system.org.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.org.service.OrganizationService;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberAddDTO;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberRoleUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrgMemberVO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationCreateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationUpdateDTO;
import com.xuejiai.aaf.module.system.org.vo.OrganizationVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 组织管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "组织管理")
@RestController
@RequestMapping("/api/system/orgs")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final ActorContext actorContext;

    @Operation(summary = "获取当前用户的组织列表")
    @GetMapping
    public Result<List<OrganizationVO>> list() {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(organizationService.listByUser(userId));
    }

    @Operation(summary = "获取组织详情")
    @GetMapping("/{id}")
    public Result<OrganizationVO> get(@PathVariable Long id) {
        return Result.success(organizationService.getById(id));
    }

    @Operation(summary = "创建组织")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OrganizationVO> create(@Validated @RequestBody OrganizationCreateDTO request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(organizationService.create(request, userId));
    }

    @Operation(summary = "更新组织")
    @PutMapping("/{id}")
    public Result<OrganizationVO> update(
            @PathVariable Long id, @Validated @RequestBody OrganizationUpdateDTO request) {
        return Result.success(organizationService.update(id, request));
    }

    @Operation(summary = "删除组织")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        organizationService.delete(id);
        return Result.success();
    }

    // ==================== 成员管理 ====================

    @Operation(summary = "获取组织成员列表")
    @GetMapping("/{orgId}/members")
    public Result<List<OrgMemberVO>> listMembers(@PathVariable Long orgId) {
        return Result.success(organizationService.listMembers(orgId));
    }

    @Operation(summary = "添加组织成员")
    @PostMapping("/{orgId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<OrgMemberVO> addMember(
            @PathVariable Long orgId, @Validated @RequestBody OrgMemberAddDTO request) {
        return Result.success(organizationService.addMember(orgId, request));
    }

    @Operation(summary = "修改成员角色")
    @PutMapping("/{orgId}/members/{memberId}/role")
    public Result<OrgMemberVO> updateMemberRole(
            @PathVariable Long orgId,
            @PathVariable Long memberId,
            @Validated @RequestBody OrgMemberRoleUpdateDTO request) {
        return Result.success(organizationService.updateMemberRole(orgId, memberId, request));
    }

    @Operation(summary = "移除组织成员")
    @DeleteMapping("/{orgId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long orgId, @PathVariable Long userId) {
        organizationService.removeMember(orgId, userId);
        return Result.success();
    }
}
