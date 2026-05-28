package com.xuejiai.aaf.module.system.skill;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 技能管理接口
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "技能管理")
@RestController
@RequestMapping("/api/system/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "查询技能列表", description = "按 assistantId 筛选，不传则查全部")
    @GetMapping
    public Result<List<SkillVO>> list(@RequestParam(required = false) String assistantId) {
        return Result.success(skillService.list(assistantId));
    }

    @Operation(summary = "获取技能详情")
    @GetMapping("/{id}")
    public Result<SkillVO> getById(@PathVariable Long id) {
        return Result.success(skillService.getById(id));
    }

    @Operation(summary = "创建技能")
    @PostMapping
    public Result<SkillVO> create(@RequestBody @Valid SkillCreateDTO dto) {
        return Result.success(skillService.create(dto));
    }

    @Operation(summary = "更新技能")
    @PutMapping("/{id}")
    public Result<SkillVO> update(@PathVariable Long id, @RequestBody @Valid SkillUpdateDTO dto) {
        return Result.success(skillService.update(id, dto));
    }

    @Operation(summary = "删除技能", description = "内置技能不可删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用/禁用技能")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        skillService.updateStatus(id, status);
        return Result.success();
    }
}
