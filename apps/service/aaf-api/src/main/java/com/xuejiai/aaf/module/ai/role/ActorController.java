/**
 * Actor（人格）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 角色管理 - Actor")
@RestController
@RequestMapping("/api/ai/actors")
@RequiredArgsConstructor
public class ActorController {

    private final AiRoleService aiRoleService;

    @Operation(summary = "Actor 列表")
    @GetMapping
    public Result<List<ActorVO>> list() {
        return Result.success(aiRoleService.listActors());
    }

    @Operation(summary = "Actor 详情")
    @GetMapping("/{id}")
    public Result<ActorVO> getById(@PathVariable Long id) {
        return Result.success(aiRoleService.getActorById(id));
    }

    @Operation(summary = "创建 Actor")
    @PostMapping
    public Result<ActorVO> create(@Validated @RequestBody ActorCreateDTO dto) {
        return Result.success(aiRoleService.createActor(dto));
    }

    @Operation(summary = "更新 Actor")
    @PutMapping("/{id}")
    public Result<ActorVO> update(@PathVariable Long id, @Validated @RequestBody ActorCreateDTO dto) {
        return Result.success(aiRoleService.updateActor(id, dto));
    }

    @Operation(summary = "删除 Actor")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiRoleService.deleteActor(id);
        return Result.success();
    }
}
