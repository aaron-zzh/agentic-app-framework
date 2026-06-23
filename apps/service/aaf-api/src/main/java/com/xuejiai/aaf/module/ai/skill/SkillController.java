package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 技能管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "技能管理")
@RestController
@RequestMapping("/api/system/skills")
@RequiredArgsConstructor
public class SkillController
        extends BaseCrudController<
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, PageParam> {

    private final SkillService skillService;

    @Override
    protected SkillService getService() {
        return skillService;
    }

    @Operation(
            summary = "查询激活技能列表",
            description = "支持按 category 过滤；activeOnly 默认 true 仅返回激活技能；按 priority 降序")
    @GetMapping("/active")
    public Result<List<SkillVO>> listActive(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        return Result.success(
                skillService.listByCategory(category, Boolean.TRUE.equals(activeOnly)));
    }

    @Operation(summary = "查询技能列表（按 owner）", description = "按 ownerId 筛选（全局 + 私有），不传则查全部")
    @GetMapping("/by-owner")
    public Result<List<SkillVO>> listByOwner(@RequestParam(required = false) Long ownerId) {
        return Result.success(skillService.list(ownerId));
    }

    @Operation(summary = "启用/禁用技能")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        skillService.updateStatus(id, status);
        return Result.success();
    }
}
