/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService.ApprovalType;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService.Decision;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * HITL 审批工具——AAF 原生，对接 {@link HumanApprovalService}。
 *
 * <p>实现：
 *
 * <ol>
 *   <li>调用 {@code HumanApprovalService.request} 创建审批请求 → 触发 SSE 推送给前端 + 钉钉/邮件等通知
 *   <li>同步轮询 {@code getResult(requestId)}（最长 5 分钟，每秒一次）
 *   <li>返回 {@code approved} / {@code rejected} / {@code timeout}
 * </ol>
 *
 * <p>调用约定：仅在「不可逆 / 高风险 / 涉及他人」动作前调用，例如发布到外部平台、调用付费 API、删除内容等。
 */
public class RequestApprovalTool {

    private static final Logger log = LoggerFactory.getLogger(RequestApprovalTool.class);

    /** 默认轮询超时（毫秒）。HITL 服务自身的超时是 5 分钟，这里设个比它略短的兜底。 */
    private static final long POLL_TIMEOUT_MS = 5 * 60_000L;

    /** 轮询间隔（毫秒）。 */
    private static final long POLL_INTERVAL_MS = 1_000L;

    private final HumanApprovalService approvalService;

    public RequestApprovalTool(HumanApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Tool(
            description =
                    "Request human approval before performing a high-risk action (publish, pay,"
                            + " delete, etc.). Blocks (up to 5 minutes) until human responds."
                            + " Returns approved/rejected/timeout.")
    public String request_approval(
            @ToolParam(
                            name = "action",
                            description = "Short action identifier (e.g., 'publish_to_wechat')")
                    String action,
            @ToolParam(
                            name = "reason",
                            description =
                                    "Why this action is needed and what would happen if approved")
                    String reason,
            @ToolParam(name = "riskLevel", description = "low | medium | high (defaults to medium)")
                    String riskLevel) {
        var userId = AafContextHolder.userId();
        var threadId = AafContextHolder.threadId();
        var lvl = (riskLevel == null || riskLevel.isBlank()) ? "medium" : riskLevel.toLowerCase();
        log.info(
                "[request_approval] action={} riskLevel={} userId={} threadId={}",
                action,
                lvl,
                userId,
                threadId);

        if (userId == null) {
            return errorJson("无当前用户上下文（forwardedProps.userId 缺失），无法发起 HITL 审批");
        }
        if (action == null || action.isBlank()) {
            return errorJson("action 不能为空");
        }

        try {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("riskLevel", lvl);
            ctx.put("subjectType", "agent_action");
            ctx.put("subjectKey", action);
            ctx.put("source", "agentscope-content-creation");

            var requestId =
                    approvalService.request(
                            threadId,
                            userId,
                            ApprovalType.ACTION_CONFIRM,
                            "Agent 高风险动作：" + action,
                            reason == null ? action : reason,
                            ctx);
            log.info("[request_approval] HITL 已创建 requestId={}", requestId);

            // 轮询审批结果
            var deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                var maybe = approvalService.getResult(requestId);
                if (maybe.isPresent()) {
                    var result = maybe.get();
                    return JsonUtils.toJsonString(Map.of(
                            "status",
                            "ok",
                            "decision",
                            result.decision().name().toLowerCase(),
                            "reason",
                            result.reason() == null ? "" : result.reason(),
                            "requestId",
                            requestId,
                            "action",
                            action));
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return errorJson("轮询被中断");
                }
            }

            // 超时——主动返回 timeout（HITL 服务侧也会同步标记）
            log.warn("[request_approval] 轮询超时 requestId={}", requestId);
            return JsonUtils.toJsonString(Map.of(
                    "status",
                    "ok",
                    "decision",
                    Decision.TIMEOUT.name().toLowerCase(),
                    "reason",
                    "审批轮询超时（>" + (POLL_TIMEOUT_MS / 1000) + " 秒）",
                    "requestId",
                    requestId,
                    "action",
                    action));
        } catch (Exception e) {
            log.error("[request_approval] 审批失败 action={}", action, e);
            return errorJson("审批失败：" + e.getMessage());
        }
    }

    private static String errorJson(String message) {
        return "{\"status\":\"error\",\"decision\":\"error\",\"message\":\""
                + message.replace("\"", "\\\"").replace("\n", "\\n")
                + "\"}";
    }
}
