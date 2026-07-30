package com.xuejiai.aaf.module.content.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.service.ContentProjectMaterializer;
import com.xuejiai.aaf.module.content.service.ContentProjectService;
import com.xuejiai.aaf.module.content.service.action.ContentActionCommandService;
import com.xuejiai.aaf.module.content.vo.ContentActionCommandDTO;
import com.xuejiai.aaf.module.content.vo.ContentActionOptionVO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectGraphVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectMaterializeDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectStatusDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectSummaryVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 内容项目接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "内容项目")
@RestController
@RequestMapping("/api/content/projects")
@RequiredArgsConstructor
public class ContentProjectController
        extends BaseCrudController<
                ContentProject,
                ContentProjectVO,
                ContentProjectCreateDTO,
                ContentProjectUpdateDTO,
                ContentProjectPageDTO> {

    private final ContentProjectService service;
    private final ContentProjectMaterializer materializer;
    private final ContentActionCommandService actionCommandService;

    @Override
    protected ContentProjectService getService() {
        return service;
    }

    @Operation(summary = "按蓝图物化内容项目")
    @PostMapping("/_materialize")
    public Result<ContentProjectVO> materialize(
            @Valid @RequestBody ContentProjectMaterializeDTO dto) {
        return Result.success(materializer.materialize(dto));
    }

    @Operation(summary = "获取项目图谱")
    @GetMapping("/{id}/graph")
    public Result<ContentProjectGraphVO> graph(@PathVariable Long id) {
        return Result.success(materializer.graph(id));
    }

    @Operation(summary = "获取项目概览")
    @GetMapping("/{id}/summary")
    public Result<ContentProjectSummaryVO> summary(@PathVariable Long id) {
        return Result.success(materializer.summary(id));
    }

    @Operation(summary = "获取项目可执行动作")
    @PreAuthorize("hasPermission(null, 'content:project:action')")
    @GetMapping("/{id}/actions")
    public Result<java.util.List<ContentActionOptionVO>> actions(@PathVariable Long id) {
        return Result.success(actionCommandService.listActions(id));
    }

    @Operation(summary = "执行项目动作")
    @PreAuthorize("hasPermission(null, 'content:project:action')")
    @PostMapping("/{id}/actions")
    public Result<ContentExecutionRunVO> executeAction(
            @PathVariable Long id, @Valid @RequestBody ContentActionCommandDTO dto) {
        return Result.success(actionCommandService.execute(id, dto));
    }

    @Operation(summary = "更新项目状态")
    @PutMapping("/{id}/status")
    public Result<ContentProjectVO> updateStatus(
            @PathVariable Long id, @Valid @RequestBody ContentProjectStatusDTO dto) {
        return Result.success(service.updateStatus(id, dto));
    }
}
