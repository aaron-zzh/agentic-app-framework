package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
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
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, SkillPageDTO> {

    private final SkillService skillService;

    @Override
    protected SkillService getService() {
        return skillService;
    }

    @Operation(summary = "查询我的技能", description = "返回当前用户在当前组织/工作区创建的技能")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<PageResult<SkillVO>> pageMine(@Validated SkillPageDTO request) {
        return Result.success(skillService.pageMine(request));
    }

    @Operation(summary = "查询公共技能", description = "返回系统技能与当前组织/工作区公开的用户技能")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public")
    public Result<PageResult<SkillVO>> pagePublic(@Validated SkillPageDTO request) {
        return Result.success(skillService.pagePublic(request));
    }

    @Operation(
            summary = "查询激活技能列表",
            description = "兼容列表接口；返回我的技能与公共技能，支持 category/activeOnly，按 priority 降序")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/active")
    public Result<List<SkillVO>> listActive(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        return Result.success(skillService.listVisible(category, Boolean.TRUE.equals(activeOnly)));
    }
}
