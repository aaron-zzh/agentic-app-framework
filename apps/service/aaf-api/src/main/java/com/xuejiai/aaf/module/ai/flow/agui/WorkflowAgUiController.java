package com.xuejiai.aaf.module.ai.flow.agui;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
 * 工作流 AG-UI 协议端点——编排画布试跑通道，启动调试流程并返回 SSE 事件流。
 *
 * <p>仅接受 {@code debug=true} 且仅工作流创建者可调用；不承载用户对话正文（唯一正文通道是 Assistant 主入口）。 契约区分与限制见 {@link
 * WorkflowAgUiService}。
 *
 * @author AaronZZH
 */
@Tag(name = "工作流 AG-UI（编排调试）")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WorkflowAgUiController {

    private final WorkflowAgUiService workflowAgUiService;

    @Operation(summary = "启动工作流调试并返回 AG-UI SSE 事件流")
    @PostMapping("/run")
    public SseEmitter run(@RequestBody @Valid WorkflowRunRequest request) {
        return workflowAgUiService.startAndStream(request);
    }

    @Operation(summary = "恢复工作流调试 AG-UI SSE 事件流")
    @GetMapping("/run/{runId}/events")
    public SseEmitter resume(@PathVariable String runId) {
        return workflowAgUiService.resumeStream(runId);
    }

    @Operation(summary = "提交用户输入（恢复等待中的流程）")
    @PostMapping("/run/{runId}/input")
    public Result<Void> submitInput(
            @PathVariable String runId, @RequestBody Map<String, Object> variables) {
        workflowAgUiService.submitInput(runId, variables);
        return Result.success();
    }
}
