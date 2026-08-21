package com.xuejiai.aaf.module.ai.prompt.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.dto.FilterPayloadParser;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.prompt.service.PromptTemplateAssetService;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptDraftDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptGovernanceActionDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCopyDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplatePageDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseDTO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateUseVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptTemplateVO;
import com.xuejiai.aaf.module.ai.prompt.vo.PromptVersionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 统一提示词资产接口；同一资源通过显式访问模式承载治理视图。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "提示词资产")
@RestController
@RequestMapping("/api/ai/prompts")
@RequiredArgsConstructor
public class PromptTemplateController
        extends BaseCrudController<
                PromptTemplate,
                PromptTemplateVO,
                PromptTemplateCreateDTO,
                PromptTemplateUpdateDTO,
                PromptTemplatePageDTO> {

    private final PromptTemplateAssetService service;
    private final HttpServletRequest httpRequest;

    @Override
    protected PromptTemplateAssetService getService() {
        return service;
    }

    @Override
    @Operation(summary = "查询提示词资产")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<PageResult<PromptTemplateVO>> page(@Validated PromptTemplatePageDTO request) {
        return Result.success(
                governanceRequested() ? service.pageGovernance(request) : service.page(request));
    }

    @Override
    @Operation(summary = "查询提示词资产窗口")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_query")
    public Result<PageResult<PromptTemplateVO>> queryWindow(
            @Validated PromptTemplatePageDTO request,
            @RequestParam(defaultValue = "list") String fieldSet,
            @RequestParam(required = false) String filter) {
        var filters = FilterPayloadParser.parse(filter);
        return Result.success(
                governanceRequested()
                        ? service.queryWindowGovernance(request, fieldSet, filters)
                        : service.queryWindow(request, fieldSet, filters));
    }

    @Override
    @Operation(summary = "查询提示词资产详情")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<PromptTemplateVO> get(
            @PathVariable Long id,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(
                governanceRequested()
                        ? service.getGovernance(id, queryToken, fieldSet)
                        : service.getById(id, queryToken, fieldSet));
    }

    @Override
    @Operation(summary = "更新提示词资产")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<PromptTemplateVO> update(
            @Parameter(description = "提示词根对象 ID") @PathVariable Long id,
            @Validated @RequestBody PromptTemplateUpdateDTO request) {
        return Result.success(
                governanceRequested()
                        ? service.updateGovernance(id, request)
                        : service.updateStudio(id, request));
    }

    @Operation(summary = "查询统一公共提示词目录")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public")
    public Result<PageResult<PromptTemplateVO>> listPublic(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var query = directoryQuery(type, scope, category, search, page + 1, size);
        return Result.success(service.pagePublic(query));
    }

    @Operation(summary = "查询 Studio 系统提示词")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/system")
    public Result<PageResult<PromptTemplateVO>> listSystem(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var query = directoryQuery(type, scope, category, search, page + 1, size);
        return Result.success(service.pageSystem(query));
    }

    @Operation(summary = "查询我的提示词")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<PageResult<PromptTemplateVO>> mine(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var query = directoryQuery(type, scope, category, search, pageNo, pageSize);
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

    @Operation(summary = "查询 Prompt 治理版本历史")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/{id}/versions")
    public Result<List<PromptVersionVO>> listVersionsGovernance(@PathVariable Long id) {
        return Result.success(service.listVersionsGovernance(id));
    }

    @Operation(summary = "创建 Prompt Draft")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/drafts")
    public Result<PromptVersionVO> createDraftGovernance(
            @PathVariable Long id, @Valid @RequestBody(required = false) PromptDraftDTO request) {
        return Result.success(service.createDraftGovernance(id, request));
    }

    @Operation(summary = "编辑 Prompt Draft")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/drafts/{version}")
    public Result<PromptVersionVO> updateDraftGovernance(
            @PathVariable Long id,
            @PathVariable int version,
            @Valid @RequestBody PromptDraftDTO request) {
        return Result.success(service.updateDraftGovernance(id, version, request));
    }

    @Operation(summary = "发布指定 Prompt Draft")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/drafts/{version}/publish")
    public Result<PromptTemplateVO> publishDraftGovernance(
            @PathVariable Long id, @PathVariable int version) {
        return Result.success(service.publishDraftGovernance(id, version));
    }

    @Operation(summary = "为 Prompt 创建 Draft", description = "通用 EntityAction，仅提交 {id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/actions/create-draft")
    public Result<PromptVersionVO> createDraftAction(
            @Valid @RequestBody PromptGovernanceActionDTO request) {
        return Result.success(service.createDraftGovernance(request.id(), null));
    }

    @Operation(summary = "发布 Prompt 最新 Draft", description = "通用 EntityAction，仅提交 {id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/actions/publish-draft")
    public Result<PromptTemplateVO> publishDraftAction(
            @Valid @RequestBody PromptGovernanceActionDTO request) {
        return Result.success(service.publishLatestDraftGovernance(request.id()));
    }

    private PromptTemplatePageDTO directoryQuery(
            String type, String scope, String category, String search, int pageNo, int pageSize) {
        var query = new PromptTemplatePageDTO();
        query.setType(type);
        query.setScope(scope);
        query.setCategory(category);
        query.setSearch(search);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    /** 只有本 Controller 明确识别治理 Header，避免全局 Header 提升其他资源。 */
    private boolean governanceRequested() {
        var value = httpRequest.getHeader(AccessMode.HTTP_HEADER);
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!AccessMode.ADMIN_MAINTENANCE.permissionSegment().equals(value.trim())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的 CRUD 访问模式");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var superAdmin =
                authentication != null
                        && authentication.getAuthorities().stream()
                                .anyMatch(
                                        authority ->
                                                "ROLE_SUPER_ADMIN"
                                                        .equals(authority.getAuthority()));
        if (!superAdmin) {
            throw new AccessDeniedException("仅 super_admin 可使用治理访问模式");
        }
        return true;
    }
}
