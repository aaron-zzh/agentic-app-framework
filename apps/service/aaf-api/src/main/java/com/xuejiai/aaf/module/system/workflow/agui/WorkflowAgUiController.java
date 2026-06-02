package com.xuejiai.aaf.module.system.workflow.agui;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 工作流 AG-UI 协议端点——通过 AG-UI 协议启动工作流并返回 SSE 事件流。
 *
 * @author Kiro
 */
@Tag(name = "工作流 AG-UI")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowAgUiController {

    private final WorkflowAgUiService workflowAgUiService;

    @Operation(summary = "启动工作流并返回 AG-UI SSE 事件流")
    @PostMapping("/run")
    public SseEmitter run(@RequestBody @Valid WorkflowRunRequest request) {
        return workflowAgUiService.startAndStream(request);
    }

    @Operation(summary = "提交用户输入（恢复等待中的流程）")
    @PostMapping("/run/{runId}/input")
    public Result<Void> submitInput(
            @PathVariable String runId, @RequestBody Map<String, Object> variables) {
        workflowAgUiService.submitInput(runId, variables);
        return Result.success();
    }
}
