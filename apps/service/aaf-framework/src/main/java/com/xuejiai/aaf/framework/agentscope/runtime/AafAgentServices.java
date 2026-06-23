/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.runtime;

import org.springframework.jdbc.core.JdbcTemplate;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.search.SimilaritySearchService;
import com.xuejiai.aaf.framework.engine.memory.AtomMemoryEngine;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrServiceFactory;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.messaging.MessageService;

/**
 * AAF Agent 工具与中间件所依赖的核心 Spring 服务集合——一次性注入，避免每个组件单独 autowire。
 *
 * @param embeddingService 文本→向量服务
 * @param memoryEngine 长期记忆引擎
 * @param kbSearch 知识库相似度检索
 * @param humanApprovalService HITL 审批服务
 * @param jdbcTemplate JDBC 模板
 * @param capabilityRouter 六层模型决策链
 * @param creditGuard 积分门控
 * @param modelRepository 模型仓储
 * @param ocrServiceFactory OCR 服务工厂
 * @param messageService 消息发送服务（站内信 / 邮件 / 钉钉 / 企微）
 * @param importerFactory 文档解析工厂（PDF/Word/Markdown/HTML）
 */
public record AafAgentServices(
        EmbeddingService embeddingService,
        AtomMemoryEngine memoryEngine,
        SimilaritySearchService kbSearch,
        HumanApprovalService humanApprovalService,
        JdbcTemplate jdbcTemplate,
        CapabilityRouter capabilityRouter,
        AiCreditGuard creditGuard,
        AiModelRepository modelRepository,
        OcrServiceFactory ocrServiceFactory,
        MessageService messageService,
        ImporterFactory importerFactory) {}
