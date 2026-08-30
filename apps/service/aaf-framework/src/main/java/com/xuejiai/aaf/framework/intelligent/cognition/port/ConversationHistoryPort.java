/**
 * 会话历史只读边界。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryMessage;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/**
 * 会话消息数据库的只读边界，用于 Redis 短期记忆 TTL 失效后的历史兜底。
 *
 * <p>Redis 短期记忆是会话级缓存（有 TTL），不是唯一真理源；完整对话历史落在业务模块自己的会话消息表。
 * 本端口只做只读兜底查询，不参与短期记忆的正常读写路径，找不到实现时按无历史降级，不阻断主执行。
 */
public interface ConversationHistoryPort {

    /** 按会话取最近若干条消息，按时间正序返回；无记录时返回空列表。 */
    List<MemoryMessage> recentMessages(
            TenantId tenantId, UserId userId, String conversationId, int limit);
}
