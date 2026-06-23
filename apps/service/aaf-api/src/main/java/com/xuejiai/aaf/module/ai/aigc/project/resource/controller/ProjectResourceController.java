package com.xuejiai.aaf.module.ai.aigc.project.resource.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.project.resource.service.UserProjectResourceService;
import com.xuejiai.aaf.module.ai.aigc.project.resource.vo.UserProjectResourceLinkDTO;
import com.xuejiai.aaf.module.ai.aigc.project.resource.vo.UserProjectResourceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 项目资源关联接口。 */
@Tag(name = "项目-资源关联")
@RestController
@RequestMapping("/api/aigc/projects/{projectId}/resources")
@RequiredArgsConstructor
public class ProjectResourceController {

    private final UserProjectResourceService resourceService;

    @Operation(summary = "列出项目资源")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<List<UserProjectResourceVO>> list(@PathVariable Long projectId) {
        return Result.success(resourceService.list(projectId));
    }

    @Operation(summary = "关联资源到项目")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<UserProjectResourceVO> link(
            @PathVariable Long projectId, @Valid @RequestBody UserProjectResourceLinkDTO dto) {
        return Result.success(resourceService.link(projectId, dto));
    }

    @Operation(summary = "解除资源关联")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public Result<Void> unlink(@PathVariable Long projectId, @PathVariable Long id) {
        resourceService.unlink(projectId, id);
        return Result.success();
    }
}
