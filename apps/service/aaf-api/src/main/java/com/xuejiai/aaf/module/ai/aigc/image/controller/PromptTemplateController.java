package com.xuejiai.aaf.module.ai.aigc.image.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.image.service.GenerationTemplateService;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 个人提示词模板接口（/api/aigc/prompt-templates）。
 *
 * <p>复用 GenerationTemplate 表和安全 CRUD，个人接口只能操作当前用户的数据。
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "个人提示词模板")
@RestController
@RequestMapping("/api/aigc/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final GenerationTemplateService templateService;
    private final OperatorContext operatorContext;

    @Operation(summary = "我的提示词模板（分页 + tag 筛选）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<PageResult<GenerationTemplateVO>> myTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var query = new GenerationTemplatePageDTO();
        query.setCategory(category);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.success(templateService.pageByUser(userId, query));
    }

    @Operation(summary = "创建个人提示词模板")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<GenerationTemplateVO> create(
            @Valid @RequestBody GenerationTemplateCreateDTO dto) {
        return Result.success(templateService.create(dto));
    }

    @Operation(summary = "更新个人提示词模板")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<GenerationTemplateVO> update(
            @PathVariable Long id, @Valid @RequestBody GenerationTemplateUpdateDTO dto) {
        return Result.success(templateService.update(id, dto));
    }

    @Operation(summary = "删除个人提示词模板")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
