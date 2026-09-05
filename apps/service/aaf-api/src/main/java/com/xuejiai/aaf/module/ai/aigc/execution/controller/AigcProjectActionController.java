package com.xuejiai.aaf.module.ai.aigc.execution.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.AigcAuthorities;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunView;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcActionCommandService;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcActionCommandDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcActionOptionVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aigc/projects")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcProjectActionController {

    private final AigcActionCommandService service;

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_READ)
    @GetMapping("/{projectId}/actions")
    public Result<List<AigcActionOptionVO>> actions(@PathVariable Long projectId) {
        return Result.success(service.listActions(projectId));
    }

    @PreAuthorize(AigcAuthorities.HAS_PROJECT_ACTION)
    @PostMapping("/{projectId}/actions")
    public Result<AigcExecutionRunView> execute(
            @PathVariable Long projectId, @Valid @RequestBody AigcActionCommandDTO request) {
        return Result.success(
                service.submit(
                        new AigcActionCommand(
                                projectId,
                                request.objectId(),
                                request.actionKey(),
                                request.prompt(),
                                request.requestedModelId(),
                                request.actionArguments(),
                                request.attachmentMediaVersionIds(),
                                request.selectedProjectObjectIds(),
                                request.expectedGraphRevision(),
                                Boolean.TRUE.equals(request.confirmed()),
                                request.idempotencyKey())));
    }
}
