package com.xuejiai.aaf.module.knowledge.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;

/** 知识库仓储。 */
public interface KnowledgeBaseRepository
        extends JpaRepository<KnowledgeBase, Long>, JpaSpecificationExecutor<KnowledgeBase> {}
