package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.function.Supplier;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 为用户提供默认 Assistant 副本的唯一写入边界。 */
public interface AssistantProvisioningPort {

    /** 幂等创建用户默认 Assistant；已存在时不修改现有定义。 */
    void provisionDefaultForUser(UserId userId);

    /**
     * 幂等保证用户默认 Assistant 绑定了可用的默认知识库。
     *
     * <p>已绑定且知识库仍可用时不调用 {@code knowledgeBaseFactory}；未绑定或绑定的知识库已删除、停用时调用它创建新库并绑定。
     *
     * @param knowledgeBaseFactory 创建知识库并返回其 {@code ai_knowledge_base.id}
     */
    void ensureDefaultKnowledgeBase(UserId userId, Supplier<Long> knowledgeBaseFactory);
}
