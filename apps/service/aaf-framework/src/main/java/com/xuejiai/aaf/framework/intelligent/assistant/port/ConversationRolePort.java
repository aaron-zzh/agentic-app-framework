package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * 会话当前角色端口（ADR-011）：只决定下一轮未锁定角色时从哪个角色起步，不复用上一轮执行现场。
 *
 * <p>由业务层按会话存储实现；未提供实现时框架等同"无会话角色"，一律从助理默认角色起步。
 */
public interface ConversationRolePort {

    /** 会话当前角色；会话不存在或未记录时返回空。 */
    Optional<String> find(TenantId tenantId, ConversationId conversationId);

    /** 记录会话当前角色；会话不存在时由实现记录日志后跳过，不抛异常。 */
    void save(TenantId tenantId, ConversationId conversationId, String roleKey);
}
