package com.xuejiai.aaf.framework.engine.knowledge.graph;

/** 知识库归属者查询端口；具体业务表持久化由上层模块实现。 */
public interface KnowledgeBaseOwnerPort {

    /**
     * 查询知识库的归属者 ID（{@code ai_knowledge_base.owner_id}），用于把实体消歧过程中的 embedding
     * 调用成本记账到正确的用户。知识库不存在或已删除时返回 {@code null}。
     */
    Long findOwnerId(Long knowledgeBaseId);
}
