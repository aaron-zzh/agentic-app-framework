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

    @Operation(summary = "查询技能列表", description = "按 assistantId 筛选，不传则查全部")
    @GetMapping("/by-assistant")
    public Result<List<SkillVO>> listByAssistant(
            @RequestParam(required = false) String assistantId) {
        return Result.success(skillService.list(assistantId));
    }

    @Operation(summary = "启用/禁用技能")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        skillService.updateStatus(id, status);
        return Result.success();
    }
}
