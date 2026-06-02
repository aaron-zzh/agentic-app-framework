package com.xuejiai.aaf.module.channel.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.hitl.ApprovalRequestPublisher;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService.ApprovalRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道审批卡片推送——当 Agent HITL 暂停时，向渠道用户推送交互式卡片消息。
 *
 * <p>实现 {@link ApprovalRequestPublisher} SPI，与 AG-UI 的 SSE 推送并行。
 * 各渠道 SDK（企微/钉钉/飞书）按各自卡片消息格式推送。
 *
 * <h3>推送效果</h3>
 * <pre>
 * ┌─────────────────────────────────┐
 * │ 🔔 操作确认                      │
 * │                                  │
 * │ Agent 请求执行以下操作：           │
 * │ 工具确认: start_workflow          │
 * │ 风险等级: MEDIUM                  │
 * │                                  │
 * │  [✅ 确认]    [❌ 拒绝]           │
 * └─────────────────────────────────┘
 * </pre>
 *
 * <h3>确认流程</h3>
 * <pre>
 * 用户点击按钮 → 渠道回调 → ChannelApprovalCallbackHandler
 *   → HumanApprovalService.resolve(requestId, decision)
 *   → ApprovalResolvedEvent 发布
 *   → HitlApprovalGrantListener 授权
 *   → 下次 Agent 调用该工具自动通过
 * </pre>
 *
 * <p>注意：渠道链路中 Agent 是通过 {@code agent.call()} 阻塞等待的，
 * resolve 后不需要像 AG-UI 那样 {@code agent.stream()} 恢复——
 * ToolPermissionChecker 下次检查时已被授权，Agent 循环自动继续。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelApprovalPublisher implements ApprovalRequestPublisher {

    // TODO: 注入各渠道 SDK（WecomClient、DingtalkClient 等）

    @Override
    public void publish(ApprovalRequest request) {
        if (request.sessionId() == null) return;

        // 仅对渠道来源的会话推送（AG-UI 会话由 SSE 推送，不需要卡片）
        // TODO: 判断 sessionId 对应的会话是否来自渠道（需 ChatSession.type/channel 字段）
        log.debug("渠道审批推送: requestId={}, type={}, title={}",
                request.requestId(), request.type(), request.title());

        // TODO: 按渠道类型分发
        // if (isWecom(session)) sendWecomCard(request);
        // if (isDingtalk(session)) sendDingtalkActionCard(request);
        // if (isFeishu(session)) sendFeishuCard(request);
    }
}
