package com.xuejiai.aaf.module.system.workflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.workflow.service.WorkflowService;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessDefinitionVO;
import com.xuejiai.aaf.module.system.workflow.vo.ProcessInstanceVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowDeployDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowMessageDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowPublishDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowSignalDTO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowVersionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 通用审批工作流接口
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/api/system/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // ==================== #5802 流程定义管理 ====================

    @Operation(summary = "分页查询流程定义")
    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProcessDefinitionVO>> queryDefinitions(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(workflowService.queryDefinitions(key, name, pageNo, pageSize));
    }

    @Operation(summary = "查询流程定义所有版本")
    @GetMapping("/definitions/{processKey}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<ProcessDefinitionVO>> listDefinitionVersions(
            @PathVariable String processKey) {
        return Result.success(workflowService.listDefinitionVersions(processKey));
    }

    @Operation(summary = "部署流程定义")
    @PostMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<String> deploy(@Validated @RequestBody WorkflowDeployDTO dto) {
        return Result.success(workflowService.deployDefinition(dto));
    }

    @Operation(summary = "挂起流程定义")
    @PutMapping("/definitions/{processDefinitionId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> suspendDefinition(@PathVariable String processDefinitionId) {
        workflowService.suspendDefinition(processDefinitionId);
        return Result.success();
    }

    @Operation(summary = "激活流程定义")
    @PutMapping("/definitions/{processDefinitionId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> activateDefinition(@PathVariable String processDefinitionId) {
        workflowService.activateDefinition(processDefinitionId);
        return Result.success();
    }

    @Operation(summary = "删除流程部署")
    @DeleteMapping("/deployments/{deploymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteDeployment(
            @PathVariable String deploymentId,
            @RequestParam(defaultValue = "false") boolean cascade) {
        workflowService.deleteDeployment(deploymentId, cascade);
        return Result.success();
    }

    @Operation(summary = "导出流程定义 XML")
    @GetMapping("/definitions/{processDefinitionId}/xml")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<String> exportDefinitionXml(@PathVariable String processDefinitionId) {
        return Result.success(workflowService.exportDefinitionXml(processDefinitionId));
    }

    // ==================== #5803 流程实例管理 ====================

    @Operation(summary = "分页查询运行中的流程实例")
    @GetMapping("/instances/running")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProcessInstanceVO>> listRunningInstances(
            @RequestParam(required = false) String processKey,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(workflowService.listRunningInstances(processKey, pageNo, pageSize));
    }

    @Operation(summary = "分页查询历史流程实例")
    @GetMapping("/instances/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<PageResult<ProcessInstanceVO>> listHistoricInstances(
            @RequestParam(required = false) String processKey,
            @RequestParam(defaultValue = "true") boolean finished,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(
                workflowService.listHistoricInstances(processKey, finished, pageNo, pageSize));
    }

    @Operation(summary = "挂起流程实例")
    @PutMapping("/instances/{processInstanceId}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> suspendInstance(@PathVariable String processInstanceId) {
        workflowService.suspendInstance(processInstanceId);
        return Result.success();
    }

    @Operation(summary = "激活流程实例")
    @PutMapping("/instances/{processInstanceId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> activateInstance(@PathVariable String processInstanceId) {
        workflowService.activateInstance(processInstanceId);
        return Result.success();
    }

    @Operation(summary = "终止流程实例")
    @PutMapping("/instances/{processInstanceId}/terminate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> terminateInstance(
            @PathVariable String processInstanceId, @RequestParam(required = false) String reason) {
        workflowService.terminateInstance(processInstanceId, reason);
        return Result.success();
    }

    @Operation(summary = "删除流程实例")
    @DeleteMapping("/instances/{processInstanceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> deleteInstance(
            @PathVariable String processInstanceId, @RequestParam(required = false) String reason) {
        workflowService.deleteInstance(processInstanceId, reason);
        return Result.success();
    }

    @Operation(summary = "设置流程变量")
    @PutMapping("/instances/{processInstanceId}/variables")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> setProcessVariables(
            @PathVariable String processInstanceId, @RequestBody Map<String, Object> variables) {
        workflowService.setProcessVariables(processInstanceId, variables);
        return Result.success();
    }

    @Operation(summary = "获取流程变量")
    @GetMapping("/instances/{processInstanceId}/variables")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> getProcessVariables(@PathVariable String processInstanceId) {
        return Result.success(workflowService.getProcessVariables(processInstanceId));
    }

    // ==================== #5805 信号与消息事件 ====================

    @Operation(summary = "发送信号事件")
    @PostMapping("/events/signal")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> sendSignal(@Validated @RequestBody WorkflowSignalDTO dto) {
        workflowService.sendSignal(dto.signalName(), dto.processInstanceId(), dto.variables());
        return Result.success();
    }

    @Operation(summary = "发送消息事件")
    @PostMapping("/events/message")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> sendMessage(@Validated @RequestBody WorkflowMessageDTO dto) {
        workflowService.sendMessage(dto.messageName(), dto.processInstanceId(), dto.variables());
        return Result.success();
    }

    // ==================== #6105 工作流发布与版本 ====================

    @Operation(summary = "发布工作流为可对话 Agent")
    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> publish(@Validated @RequestBody WorkflowPublishDTO dto) {
        workflowService.publishWorkflow(dto);
        return Result.success();
    }

    @Operation(summary = "查询工作流版本列表")
    @GetMapping("/versions/{processKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<WorkflowVersionVO>> listVersions(@PathVariable String processKey) {
        return Result.success(workflowService.listVersions(processKey));
    }

    @Operation(summary = "激活指定版本")
    @PostMapping("/versions/{processKey}/activate/{version}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> activateVersion(
            @PathVariable String processKey, @PathVariable int version) {
        workflowService.activateVersion(processKey, version);
        return Result.success();
    }
}
