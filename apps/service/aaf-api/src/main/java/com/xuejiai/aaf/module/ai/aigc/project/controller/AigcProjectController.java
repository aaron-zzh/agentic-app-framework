package com.xuejiai.aaf.module.ai.aigc.project.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProject;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectCreateDTO;
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
}
