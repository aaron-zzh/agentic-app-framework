package com.xuejiai.aaf.module.knowledge.api;

/** 知识模块对用户域暴露的个人知识库创建接口。 */
public interface PersonalKnowledgeApi {

    /**
     * 为用户创建一个空的个人默认知识库并返回其 id。
     *
     * @param userId 知识库所有者
     * @param orgId 所有者的个人组织；可为空，为空时知识库不归属任何组织
     */
    Long createDefaultKnowledgeBase(Long userId, Long orgId);
}
