package com.xuejiai.aaf.module.ai.role;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 角色分类接口。
 *
 * <p>携带治理 Header {@code X-AAF-Access-Mode: admin-maintenance} 时走治理入口；治理权限由 CRUD 安全管线按 {@code
 * system:role-category:access-mode:admin-maintenance} 与普通动作权限共同校验。
 */
@Tag(name = "AI 角色分类")
@RestController
@RequestMapping(RoleCategoryResource.BASE_PATH)
@RequiredArgsConstructor
public class RoleCategoryController
        extends BaseCrudController<
                AiRoleCategory,
                RoleCategoryVO,
                RoleCategoryCreateDTO,
                RoleCategoryUpdateDTO,
                PageParam> {

    private final RoleCategoryService roleCategoryService;
    private final HttpServletRequest httpRequest;

    @Override
    protected RoleCategoryService getService() {
        return roleCategoryService;
    }

    @Override
    @Operation(summary = "查询角色分类")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<PageResult<RoleCategoryVO>> page(@Validated PageParam request) {
        return Result.success(
                governanceRequested()
                        ? roleCategoryService.pageGovernance(request)
                        : roleCategoryService.page(request));
    }

    @Override
    @Operation(summary = "查询角色分类窗口")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_query")
    public Result<PageResult<RoleCategoryVO>> queryWindow(
            @Validated PageParam request,
            @RequestParam(defaultValue = "list") String fieldSet,
            @RequestParam(required = false) String filter) {
        var filters = parseFilters(filter);
        return Result.success(
                governanceRequested()
                        ? roleCategoryService.queryWindowGovernance(request, fieldSet, filters)
                        : roleCategoryService.queryWindow(request, fieldSet, filters));
    }

    @Override
    @Operation(summary = "查询角色分类详情")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<RoleCategoryVO> get(
            @PathVariable Long id,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(
                governanceRequested()
                        ? roleCategoryService.getGovernance(id, queryToken, fieldSet)
                        : roleCategoryService.getById(id, queryToken, fieldSet));
    }

    @Override
    @Operation(summary = "创建角色分类", description = "普通访问创建本人私有分类；治理访问创建系统分类")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RoleCategoryVO> create(@Validated @RequestBody RoleCategoryCreateDTO request) {
        return Result.success(
                governanceRequested()
                        ? roleCategoryService.createGovernance(request)
                        : roleCategoryService.create(request));
    }

    @Override
    @Operation(summary = "更新角色分类")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<RoleCategoryVO> update(
            @Parameter(description = "分类 ID") @PathVariable Long id,
            @Validated @RequestBody RoleCategoryUpdateDTO request) {
        return Result.success(
                governanceRequested()
                        ? roleCategoryService.updateGovernance(id, request)
                        : roleCategoryService.update(id, request));
    }

    @Override
    @Operation(summary = "删除角色分类", description = "私有分类删除后其角色变为未分类；被引用的系统分类拒绝删除")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "分类 ID") @PathVariable Long id) {
        if (governanceRequested()) {
            roleCategoryService.deleteGovernance(id);
        } else {
            roleCategoryService.delete(id);
        }
        return Result.success();
    }

    /** 只识别 admin-maintenance；是否有权使用由 CRUD 安全管线的访问模式权限码判定。 */
    private boolean governanceRequested() {
        var value = httpRequest.getHeader(AccessMode.HTTP_HEADER);
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!AccessMode.ADMIN_MAINTENANCE.permissionSegment().equals(value.trim())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的 CRUD 访问模式");
        }
        return true;
    }
}
