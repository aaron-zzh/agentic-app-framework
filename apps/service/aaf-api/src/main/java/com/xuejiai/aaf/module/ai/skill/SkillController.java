package com.xuejiai.aaf.module.ai.skill;

import java.util.List;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.enforcement.AccessMode;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/** Skill 根对象与不可变版本管理接口；治理视图复用同一 ai.skill 资源。 */
@Tag(name = "Skill 管理")
@RestController
@RequestMapping("/api/system/skills")
@RequiredArgsConstructor
public class SkillController
        extends BaseCrudController<
                SkillDefinition, SkillVO, SkillCreateDTO, SkillUpdateDTO, SkillPageDTO> {

    private final SkillService skillService;
    private final HttpServletRequest httpRequest;

    @Override
    protected SkillService getService() {
        return skillService;
    }

    @Override
    @Operation(summary = "查询 Skill")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<PageResult<SkillVO>> page(@Validated SkillPageDTO request) {
        return Result.success(
                governanceRequested()
                        ? skillService.pageGovernance(request)
                        : skillService.page(request));
    }

    @Override
    @Operation(summary = "查询 Skill 窗口")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/_query")
    public Result<PageResult<SkillVO>> queryWindow(
            @Validated SkillPageDTO request,
            @RequestParam(defaultValue = "list") String fieldSet,
            @RequestParam(required = false) String filter) {
        var filters = parseFilters(filter);
        return Result.success(
                governanceRequested()
                        ? skillService.queryWindowGovernance(request, fieldSet, filters)
                        : skillService.queryWindow(request, fieldSet, filters));
    }

    @Override
    @Operation(summary = "查询 Skill 详情")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<SkillVO> get(
            @PathVariable Long id,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(
                governanceRequested()
                        ? skillService.getGovernance(id, queryToken, fieldSet)
                        : skillService.getById(id, queryToken, fieldSet));
    }

    @Override
    @Operation(summary = "创建 Skill 根对象和首个不可变版本")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<SkillVO> create(@Validated @RequestBody SkillCreateDTO request) {
        return Result.success(
                governanceRequested()
                        ? skillService.createVersionedGovernance(request)
                        : skillService.createVersioned(request));
    }

    @Override
    @Operation(summary = "更新 Skill", description = "正文或需求变化会追加新版本，不修改历史版本")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<SkillVO> update(
            @Parameter(description = "Skill 根对象 ID") @PathVariable Long id,
            @Validated @RequestBody SkillUpdateDTO request) {
        return Result.success(
                governanceRequested()
                        ? skillService.updateVersionedGovernance(id, request)
                        : skillService.updateVersioned(id, request));
    }

    @Operation(summary = "查询我的 Skill")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<PageResult<SkillVO>> pageMine(@Validated SkillPageDTO request) {
        return Result.success(skillService.pageMine(request));
    }

    @Operation(summary = "查询公共 Skill", description = "只返回已有 current 发布版本的公共 Skill")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/public")
    public Result<PageResult<SkillVO>> pagePublic(@Validated SkillPageDTO request) {
        return Result.success(skillService.pagePublic(request));
    }

    @Operation(summary = "查询当前用户可见 Skill")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/visible")
    public Result<List<SkillVO>> listVisible(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false, defaultValue = "true") Boolean publishedOnly,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String roleKey) {
        return Result.success(
                skillService.listVisible(
                        locale, Boolean.TRUE.equals(publishedOnly), categoryCode, roleKey));
    }

    @Operation(summary = "查询 Skill 版本历史")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/versions")
    public Result<List<SkillVO.SkillVersionVO>> listVersions(@PathVariable Long id) {
        return Result.success(
                governanceRequested()
                        ? skillService.listVersionsGovernance(id)
                        : skillService.listVersions(id));
    }

    @Operation(summary = "发布 APPROVED 版本", description = "仅将已审核版本设置为 current")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/versions/{versionId}/publish")
    public Result<SkillVO> publish(@PathVariable Long id, @PathVariable Long versionId) {
        return Result.success(
                governanceRequested()
                        ? skillService.publishGovernance(id, versionId)
                        : skillService.publish(id, versionId));
    }

    @Operation(summary = "发布 Skill 最新 APPROVED 版本", description = "通用 EntityAction，仅提交 {id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/actions/publish-latest")
    public Result<SkillVO> publishLatest(@Valid @RequestBody SkillActionDTO request) {
        return Result.success(skillService.publishLatestApprovedGovernance(request.id()));
    }

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

    public record SkillActionDTO(@NotNull @Positive Long id) {}
}
