/**
 * Human-in-the-Loop（HITL）人机协作审批机制。
 *
 * <p>职责：AI 执行中需人工介入时的统一审批通知、决策收集、授权恢复。
 *
 * <p>核心类：
 * <ul>
 *   <li>{@code HumanApprovalService} — 审批请求发起/响应/查询</li>
 *   <li>{@code HumanApprovalController} — REST 端点（/api/assistant/approvals）</li>
 *   <li>{@code ApprovalRequestPublisher} — 审批推送 SPI</li>
 *   <li>{@code ApprovalEventStreamService} — SSE 推送实现</li>
 *   <li>{@code HitlApprovalGrantListener} — 审批通过→工具授权桥</li>
 *   <li>{@code AssistantSessionTrustService} — 会话级信任管理</li>
 * </ul>
 *
 * <p>与 AG-UI HITL（{@code AafToolPermissionHook.stopAgent()}）的关系：
 * AG-UI 路径负责 Agent 执行层暂停/恢复，本包负责通知层（推送审批事件、记录历史、管理信任）。
 */
package com.xuejiai.aaf.framework.intelligent.assistant.hitl;
