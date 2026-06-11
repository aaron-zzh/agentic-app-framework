package com.xuejiai.aaf.module.ai.aigc.project.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 创作项目接口。 */
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
