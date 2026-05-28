/**
 * 资源关系管理接口（ReBAC）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.relation;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "资源关系管理（ReBAC）")
@RestController
@RequestMapping("/api/system/resource-relations")
@RequiredArgsConstructor
public class ResourceRelationController {

    private final ResourceRelationService service;

    @Operation(summary = "授予资源关系")
    @PostMapping("/grant")
    public Result<Void> grant(@Validated @RequestBody GrantRelationDTO dto) {
        service.grant(dto);
        return Result.success();
    }

    @Operation(summary = "撤销资源关系")
    @PostMapping("/revoke")
    public Result<Void> revoke(@Validated @RequestBody GrantRelationDTO dto) {
        service.revoke(dto);
        return Result.success();
    }

    @Operation(summary = "检查是否拥有关系")
    @GetMapping("/check")
    public Result<Boolean> check(
            @RequestParam String resourceType, @RequestParam Long resourceId,
            @RequestParam String relation, @RequestParam String subjectType, @RequestParam Long subjectId) {
        return Result.success(service.check(resourceType, resourceId, relation, subjectType, subjectId));
    }

    @Operation(summary = "查询资源的所有关系")
    @GetMapping
    public Result<List<ResourceRelationVO>> list(
            @RequestParam String resourceType, @RequestParam Long resourceId) {
        return Result.success(service.listByResource(resourceType, resourceId));
    }
}
