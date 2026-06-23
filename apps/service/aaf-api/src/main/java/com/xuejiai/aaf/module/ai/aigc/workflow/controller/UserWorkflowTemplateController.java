package com.xuejiai.aaf.module.ai.aigc.workflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.workflow.domain.UserWorkflowTemplate;
import com.xuejiai.aaf.module.ai.aigc.workflow.service.UserWorkflowTemplateService;
import com.xuejiai.aaf.module.ai.aigc.workflow.vo.UserWorkflowTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.workflow.vo.UserWorkflowTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 用户工作流模板接口（v0.2.1 P1，只读 + run 计数）。 */
@Tag(name = "用户工作流模板")
@RestController
@RequestMapping("/api/aigc/workflow-templates")
@RequiredArgsConstructor
public class UserWorkflowTemplateController
        extends BaseCrudController<
                UserWorkflowTemplate,
                UserWorkflowTemplateVO,
                Void,
                Void,
                UserWorkflowTemplatePageDTO> {

    private final UserWorkflowTemplateService service;

    @Override
    protected UserWorkflowTemplateService getService() {
        return service;
    }

    @Operation(summary = "增加运行计数（前端运行模板前调用）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/run-count")
    public Result<Void> incrementRunCount(@PathVariable Long id) {
        service.incrementRunCount(id);
        return Result.success();
    }
}
