package com.xuejiai.aaf.module.knowledge.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.module.knowledge.api.PersonalKnowledgeApi;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;

import lombok.RequiredArgsConstructor;

/** 个人默认知识库创建适配器；注册和登录补建时没有请求租户上下文，租户归属由调用方显式传入。 */
@Service
@RequiredArgsConstructor
public class DefaultPersonalKnowledgeApi implements PersonalKnowledgeApi {

    private static final String DEFAULT_NAME = "我的知识库";
    private static final String DEFAULT_DESCRIPTION = "默认知识库，助理回答问题时自动检索，可在其中上传个人资料";

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingProperties embeddingProperties;

    @Override
    @Transactional
    public Long createDefaultKnowledgeBase(Long userId, Long orgId) {
        Objects.requireNonNull(userId, "userId 不能为空");
        var entity = new KnowledgeBase();
        entity.setName(DEFAULT_NAME);
        entity.setDescription(DEFAULT_DESCRIPTION);
        entity.setVisibility("PRIVATE");
        entity.setScopeCode("user:" + userId);
        entity.setEmbeddingModel(embeddingProperties.model());
        entity.setOwnerId(userId);
        entity.setOrgId(orgId);
        return knowledgeBaseRepository.save(entity).getId();
    }
}
