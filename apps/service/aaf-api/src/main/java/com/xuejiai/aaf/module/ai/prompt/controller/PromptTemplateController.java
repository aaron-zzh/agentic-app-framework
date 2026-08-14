package com.xuejiai.aaf.module.ai.prompt.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.prompt.service.PromptTemplateAssetService;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCopyDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplatePageDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 统一提示词资产接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "提示词资产")
@RestController
@RequestMapping("/api/aigc/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController
        extends BaseCrudController<
                PromptTemplate,
                PromptTemplateVO,
                PromptTemplateCreateDTO,
                PromptTemplateUpdateDTO,
                PromptTemplatePageDTO> {

    private final PromptTemplateAssetService service;

    @Override
    protected PromptTemplateAssetService getService() {
        return service;
    }

    @Operation(summary = "查询当前组织/工作区公开提示词")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public")
    public Result<PageResult<PromptTemplateVO>> listPublic(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var query = new PromptTemplatePageDTO();
        query.setType(type);
        query.setScope(scope);
        query.setCategory(category);
        query.setPageNo(page + 1);
        query.setPageSize(size);
        return Result.success(service.pagePublic(query));
    }

    @Operation(summary = "查询 Studio 系统提示词")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/system")
    public Result<PageResult<PromptTemplateVO>> listSystem(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var query = new PromptTemplatePageDTO();
        query.setType(type);
        query.setScope(scope);
        query.setCategory(category);
        query.setPageNo(page + 1);
        query.setPageSize(size);
        return Result.success(service.pageSystem(query));
    }

    @Operation(summary = "查询我的提示词")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<PageResult<PromptTemplateVO>> mine(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var query = new PromptTemplatePageDTO();
        query.setType(type);
        query.setScope(scope);
        query.setCategory(category);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.success(service.pageMine(query));
    }

    @Operation(summary = "安全编译并使用提示词")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/use")
    public Result<PromptTemplateUseVO> use(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PromptTemplateUseDTO request) {
        return Result.success(service.use(id, request));
    }

    @Operation(summary = "复制提示词为我的私有资产")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/copy")
    public Result<PromptTemplateVO> copy(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) PromptTemplateCopyDTO request) {
        return Result.success(service.copy(id, request));
    }
}
