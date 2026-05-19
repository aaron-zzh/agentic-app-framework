package com.xuejiai.aaf.module.system.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.service.DelegationService;
import com.xuejiai.aaf.module.system.service.WorkflowService;
import com.xuejiai.aaf.module.system.vo.WorkflowActionDTO;
import com.xuejiai.aaf.module.system.vo.WorkflowStartDTO;
import com.xuejiai.aaf.module.system.vo.WorkflowStatusVO;
import com.xuejiai.aaf.module.system.vo.WorkflowTransferDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 通用审批工作流接口。 */
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final DelegationService delegationService;
    private final ActorContext actorContext;

    @Operation(summary = "启动审批流程")
    @PostMapping("/start")
    public Result<String> start(@Validated @RequestBody WorkflowStartDTO dto) {
        String initiator = actorContext.currentUserId().orElseThrow().toString();
        String processInstanceId =
                workflowService.startProcess(dto.entityType(), dto.entityId(), initiator, dto.assignee());
        return Result.success(processInstanceId);
    }

    @Operation(summary = "通过审批")
    @PostMapping("/complete")
    public Result<Void> complete(@Validated @RequestBody WorkflowActionDTO dto) {
        workflowService.completeTask(dto.taskId(), dto.comment());
        return Result.success();
    }

    @Operation(summary = "驳回审批")
    @PostMapping("/reject")
    public Result<Void> reject(@Validated @RequestBody WorkflowActionDTO dto) {
        workflowService.rejectTask(dto.taskId(), dto.comment());
        return Result.success();
    }

    @Operation(summary = "查询流程状态")
    @GetMapping("/{processInstanceId}")
    public Result<WorkflowStatusVO> getStatus(@PathVariable String processInstanceId) {
        return Result.success(workflowService.getStatus(processInstanceId));
    }

    @Operation(summary = "查询审批历史")
    @GetMapping("/{processInstanceId}/history")
    public Result<List<WorkflowStatusVO.HistoryItem>> getHistory(@PathVariable String processInstanceId) {
        return Result.success(workflowService.getHistory(processInstanceId));
    }

    @Operation(summary = "单次转交任务")
    @PostMapping("/transfer")
    public Result<Void> transfer(@Validated @RequestBody WorkflowTransferDTO dto) {
        delegationService.transfer(dto);
        return Result.success();
    }
}
