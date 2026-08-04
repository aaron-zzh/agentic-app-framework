package com.xuejiai.aaf.module.ai.aigc.execution.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunView;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionRunService;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionRunPageDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionRunVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AIGC 执行记录")
@RestController
@RequestMapping("/api/aigc/execution-runs")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcExecutionRunController
        extends BaseCrudController<
                AigcExecutionRun, AigcExecutionRunVO, Void, Void, AigcExecutionRunPageDTO> {

    private final AigcExecutionRunService service;
    private final AigcActionCommandService commandService;

    @Override
    protected AigcExecutionRunService getService() {
        return service;
    }

    @PreAuthorize("hasAuthority('aigc:execution-run:execute')")
    @PostMapping("/{id}/_cancel")
    public Result<AigcExecutionRunView> cancel(
            @PathVariable Long id, @RequestParam(required = false) String reason) {
        return Result.success(commandService.cancel(id, reason));
    }

    @PreAuthorize("hasAuthority('aigc:execution-run:execute')")
    @PostMapping("/{id}/_retry")
    public Result<AigcExecutionRunView> retry(
            @PathVariable Long id, @RequestParam String idempotencyKey) {
        return Result.success(commandService.retry(id, idempotencyKey));
    }
}
