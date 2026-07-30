package com.xuejiai.aaf.module.content.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.service.ContentExecutionRunService;
import com.xuejiai.aaf.module.content.service.action.ContentActionCommandService;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 执行记录接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "执行记录")
@RestController
@RequestMapping("/api/content/execution-runs")
@RequiredArgsConstructor
public class ContentExecutionRunController
        extends BaseCrudController<
                ContentExecutionRun,
                ContentExecutionRunVO,
                ContentExecutionRunCreateDTO,
                ContentExecutionRunUpdateDTO,
                ContentExecutionRunPageDTO> {

    private final ContentExecutionRunService service;
    private final ContentActionCommandService actionCommandService;

    @Override
    protected ContentExecutionRunService getService() {
        return service;
    }

    @Operation(summary = "取消执行记录")
    @PreAuthorize("hasPermission(null, 'content:project:action')")
    @PostMapping("/{id}/_cancel")
    public Result<ContentExecutionRunVO> cancel(@PathVariable Long id) {
        return Result.success(actionCommandService.cancel(id));
    }

    @Operation(summary = "重试执行记录")
    @PreAuthorize("hasPermission(null, 'content:project:action')")
    @PostMapping("/{id}/_retry")
    public Result<ContentExecutionRunVO> retry(@PathVariable Long id) {
        return Result.success(actionCommandService.retry(id));
    }
}
