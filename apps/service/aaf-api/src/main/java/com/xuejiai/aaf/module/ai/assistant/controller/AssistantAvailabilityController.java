package com.xuejiai.aaf.module.ai.assistant.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantSummaryVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 当前用户可用 Assistant 查询入口（AAF-107 #10708）。
 *
 * <p>供前端角色/技能选择器展示真实可选项——之前 webui 侧 {@code useAssistants()} 调用的路径在后端一直不存在，
 * 角色下拉框全靠硬编码兜底数据运行，选择结果无法真正生效。
 */
@Tag(name = "Assistant 可用性查询")
@RestController
@RequestMapping("/api/ai/assistants")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssistantAvailabilityController {

    private final AssistantExecutionService executions;

    @Operation(summary = "查询当前用户可用的 Assistant 及其 Role 列表")
    @GetMapping("/available")
    public Result<List<AssistantSummaryVO>> available() {
        return Result.success(executions.availableAssistants());
    }
}
