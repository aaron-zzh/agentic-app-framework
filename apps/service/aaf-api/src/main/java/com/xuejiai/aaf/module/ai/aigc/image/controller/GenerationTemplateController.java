package com.xuejiai.aaf.module.ai.aigc.image.controller;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.image.domain.GenerationTemplate;
import com.xuejiai.aaf.module.ai.aigc.image.service.GenerationTemplateService;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.image.vo.GenerationTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 参数模板接口。
 *
 * @author AaronZZH & Kiro
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 参数模板")
@RestController
@RequestMapping("/api/aigc/templates")
@RequiredArgsConstructor
public class GenerationTemplateController
        extends BaseCrudController<
                GenerationTemplate,
                GenerationTemplateVO,
                GenerationTemplateCreateDTO,
                GenerationTemplateUpdateDTO,
                GenerationTemplatePageDTO> {

    private final GenerationTemplateService templateService;

    @Override
    protected GenerationTemplateService getService() {
        return templateService;
    }

    /** 查询公开模板（前端模板库使用，按 type + scope 过滤）。 */
    @Operation(summary = "查询公开模板")
    @GetMapping("/public")
    public Result<PageResult<GenerationTemplateVO>> listPublic(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var query = new GenerationTemplatePageDTO();
        query.setType(type);
        query.setScope(scope);
        query.setCategory(category);
        query.setIsPublic(true);
        query.setPageNo(page + 1); // PageParam 是 1-based
        query.setPageSize(size);
        return Result.success(templateService.page(query));
    }

    /** 使用模板（增加使用计数）。 */
    @Operation(summary = "使用模板")
    @PostMapping("/{id}/use")
    public Result<GenerationTemplateVO> use(@PathVariable Long id) {
        return Result.success(templateService.incrementUsage(id));
    }
}
