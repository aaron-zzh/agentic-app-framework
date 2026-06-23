package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
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
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocLinkDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectDocVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectSummaryVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 创作项目接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 创作项目")
@RestController
@RequestMapping("/api/aigc/projects")
@RequiredArgsConstructor
public class AigcProjectController
        extends BaseCrudController<
                AigcProject,
                AigcProjectVO,
                AigcProjectCreateDTO,
                AigcProjectUpdateDTO,
                AigcProjectPageDTO> {

    private final AigcProjectService service;

    @Override
    protected BaseCrudService<
                    AigcProject,
                    AigcProjectVO,
                    AigcProjectCreateDTO,
                    AigcProjectUpdateDTO,
                    AigcProjectPageDTO>
            getService() {
        return service;
    }

    // BE-8 数据隔离：override 单条查询，加 ownership 校验（跨用户返回 404 防探测）
    @Override
    @Operation(summary = "查询项目详情（含 ownership 校验）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<AigcProjectVO> get(
            @Parameter(description = "记录 ID") @PathVariable Long id,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(service.getByIdOwned(id));
    }

    // BE-8 数据隔离：override 更新，加 ownership 校验（跨用户返回 404 防探测）
    @Override
    @Operation(summary = "更新项目（含 ownership 校验）")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public Result<AigcProjectVO> update(
            @Parameter(description = "记录 ID") @PathVariable Long id,
            @RequestBody AigcProjectUpdateDTO request) {
        return Result.success(service.updateOwned(id, request));
    }

    // BE-8 数据隔离：override 删除，加 ownership 校验（跨用户返回 404 防探测）
    @Override
    @Operation(summary = "删除项目（含 ownership 校验）")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "记录 ID") @PathVariable Long id) {
        service.deleteOwned(id);
        return Result.success();
    }

    @Operation(summary = "项目概览统计")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/summary")
    public Result<AigcProjectSummaryVO> summary(@PathVariable Long id) {
        return Result.success(service.getSummary(id));
    }

    @Operation(summary = "获取项目关联文档列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/docs")
    public Result<List<AigcProjectDocVO>> getDocs(@PathVariable Long id) {
        return Result.success(service.getProjectDocs(id));
    }

    @Operation(summary = "关联文档到项目")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/docs")
    public Result<AigcProjectDocVO> linkDoc(
            @PathVariable Long id, @RequestBody AigcProjectDocLinkDTO dto) {
        return Result.success(service.linkDoc(id, dto));
    }

    @Operation(summary = "取消文档与项目的关联")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/docs/{docId}")
    public Result<Void> unlinkDoc(@PathVariable Long id, @PathVariable Long docId) {
        service.unlinkDoc(id, docId);
        return Result.success();
    }
}
