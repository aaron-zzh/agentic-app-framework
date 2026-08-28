package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 短期会话记忆召回边界。
 *
 * <p>短期记忆是 L1 记忆分类之一（会话 TTL），由记忆读管道作为一个通道消费，<b>不构成独立的上下文 scope</b>。原始会话消息仍归 L3
 * 会话所有，本端口只返回受预算约束的最近交互，不得复制为无期限长期记忆。
 */
public interface SessionMemoryPort {

    /** 按会话取最近若干轮交互；无记录时返回空列表。 */
    List<SessionTurn> recentTurns(SessionRecallQuery query);

    /**
     * 追加一轮用户输入与助理回复。
     *
     * <p>只写入会话 TTL 内的短期上下文，<b>不构成长期记忆沉淀</b>；写入失败按尽力而为处理，不得阻断本轮执行。
     */
    void appendTurn(
            TenantId tenantId, UserId userId, String sessionId, String userText, String replyText);

    /**
     * 会话召回请求。
     *
     * @param maxTurns 最多返回的交互条数，必须为正
     */
    record SessionRecallQuery(TenantId tenantId, UserId userId, String sessionId, int maxTurns) {
        public SessionRecallQuery {
            Objects.requireNonNull(tenantId, "tenantId 不能为空");
            Objects.requireNonNull(userId, "userId 不能为空");
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId 不能为空白");
            }
            sessionId = sessionId.trim();
            if (maxTurns <= 0) {
                throw new IllegalArgumentException("maxTurns 必须为正数");
            }
        }
    }

    /**
     * 一条会话交互。
     *
     * @param role 发言角色，取 {@code user} 或 {@code assistant}
     * @param content 交互正文；由调用方按字符预算裁剪
     */
    record SessionTurn(String role, String content) {
        public SessionTurn {
            role = requireText(role, "role");
            content = requireText(content, "content");
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空白");
            }
            return value.trim();
        }
    }
}
