/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.config.ContentCreationProperties;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.agentscope.middleware.CallLogMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.ConversationBridgeMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.FileProcessingMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.MessageQueueMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.ModelCallLimitMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.ThinkingMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.ThreadBudgetMiddleware;
import com.xuejiai.aaf.framework.agentscope.middleware.UiEventMiddleware;
import com.xuejiai.aaf.framework.agentscope.model.ContentCreationModelFactory;
import com.xuejiai.aaf.framework.agentscope.prompt.ContentCreationSystemPrompt;
import com.xuejiai.aaf.framework.agentscope.prompt.EditorSystemPrompt;
import com.xuejiai.aaf.framework.agentscope.runtime.AafAgentServices;
import com.xuejiai.aaf.framework.agentscope.session.tool.SessionsTool;
import com.xuejiai.aaf.framework.agentscope.tool.KnowledgeSearchTool;
import com.xuejiai.aaf.framework.agentscope.tool.MemoryRecallTool;
import com.xuejiai.aaf.framework.agentscope.tool.MemoryWriteTool;
import com.xuejiai.aaf.framework.agentscope.tool.OcrAgentTool;
import com.xuejiai.aaf.framework.agentscope.tool.RequestApprovalTool;
import com.xuejiai.aaf.framework.agentscope.tool.SendMessageTool;
import com.xuejiai.aaf.framework.agentscope.tool.SendUiTool;
import com.xuejiai.aaf.framework.agentscope.tool.SwitchKbTool;
import com.xuejiai.aaf.framework.agentscope.tool.UpdatePersonaTool;
import com.xuejiai.aaf.framework.agentscope.tool.WeatherAgentTool;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;

/**
 * 内容创作主 + 编辑子 Agent 工厂。
 *
 * <p>把 codingagent 示例的 {@code CodingAgentFactory} 完整改造成内容创作语义：
 *
 * <ul>
 *   <li>系统提示词替换为 {@link ContentCreationSystemPrompt} / {@link EditorSystemPrompt}
 *   <li>工具替换为 AAF 原生工具（KB / Memory / HITL）
 *   <li>沙箱按 {@link ContentCreationProperties#getSandboxType()} 选择 docker 或 none
 *   <li>启用计划模式（todo_write）+ 上下文压缩
 *   <li>注入示例移植的 session/middleware 基础设施：MessageQueueMiddleware / ThreadBudgetMiddleware /
 *       ModelCallLimitMiddleware + externalSubagentTool（SessionsTool）
 * </ul>
 */
public final class ContentCreationAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(ContentCreationAgentFactory.class);

    private ContentCreationAgentFactory() {}

    /**
     * 构建主 Agent（agentId=content-creation）。
     *
     * @param props 配置
     * @param services AAF 业务服务集合（embedding / 记忆 / 知识库 / HITL）
     * @param baseStore harness BaseStore（消息队列 / 线程元数据 / 投递去重共用），允许为空（中间件会优雅降级）
     * @param sessionsTool 子 Agent 派发工具（spawn_subagent），允许为空
     * @param resolvedModel 六层决策链解析后的 AiModel；为 null 时回退到 props.modelId
     * @param weatherTool 天气工具，允许为空（彩云 API 未配置时降级不注册）
     * @param skillPrompt 技能提示词段（由 SkillContextLoader 生成），允许为空
     */
    public static HarnessAgent createMainAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            BaseStore baseStore,
            SessionsTool sessionsTool,
            AiModel resolvedModel,
            WeatherAgentTool weatherTool,
            String skillPrompt,
            String personaPrompt,
            String kbContext,
            String assistantPrompt,
            com.xuejiai.aaf.framework.agentscope.tool.GenerateImageTool generateImageTool,
            com.xuejiai.aaf.framework.agentscope.tool.GenerateVideoTool generateVideoTool,
            com.xuejiai.aaf.framework.agentscope.tool.GenerateMusicTool generateMusicTool,
            AiProperties aiProperties) {
        var workspaceDir = props.getWorkspaceRoot().toAbsolutePath().toString();
        var toolkit = new Toolkit();
        toolkit.registerTool(
                new KnowledgeSearchTool(services.kbSearch(), services.embeddingService()));
        toolkit.registerTool(
                new MemoryRecallTool(services.memoryEngine(), services.embeddingService()));
        toolkit.registerTool(
                new MemoryWriteTool(services.memoryEngine(), services.embeddingService()));
        toolkit.registerTool(new RequestApprovalTool(services.humanApprovalService()));
        toolkit.registerTool(new SwitchKbTool(services.jdbcTemplate()));
        // OCR 工具（依赖 OcrServiceFactory）
        if (services.ocrServiceFactory() != null) {
            toolkit.registerTool(
                    new OcrAgentTool(services.ocrServiceFactory(), services.capabilityRouter()));
        }
        // 天气工具（可选）
        if (weatherTool != null) {
            toolkit.registerTool(weatherTool);
        }
        // 图像生成工具（可选，由 aaf-api 层注入）
        if (generateImageTool != null) {
            toolkit.registerTool(generateImageTool);
        }
        if (generateVideoTool != null) {
            toolkit.registerTool(generateVideoTool);
        }
        if (generateMusicTool != null) {
            toolkit.registerTool(generateMusicTool);
        }
        // 消息发送工具
        if (services.messageService() != null) {
            toolkit.registerTool(new SendMessageTool(services.messageService()));
        }
        // UI 发送工具（配合 UiEventMiddleware 使用）
        toolkit.registerTool(new SendUiTool());
        // 用户画像更新工具
        toolkit.registerTool(new UpdatePersonaTool(services.jdbcTemplate()));

        var model =
                resolvedModel != null
                        ? ContentCreationModelFactory.buildFromAiModel(resolvedModel, aiProperties)
                        : ContentCreationModelFactory.build(props);

        var builder =
                HarnessAgent.builder()
                        .agentId(props.getMainAgentId())
                        .name("AAF 内容创作助理")
                        .description("基于 HarnessAgent 的内容创作 Agent，支持知识库检索、长期记忆、HITL 审批与子 Agent 派发")
                        .model(model)
                        .sysPrompt(
                                ContentCreationSystemPrompt.build(
                                        workspaceDir, skillPrompt, personaPrompt, kbContext, assistantPrompt))
                        .workspace(props.getWorkspaceRoot())
                        .toolkit(toolkit)
                        .maxIters(props.getMaxIterations())
                        .compaction(
                                CompactionConfig.builder()
                                        .triggerMessages(props.getCompactionTriggerMessages())
                                        .keepMessages(props.getCompactionKeepMessages())
                                        .flushBeforeCompact(true)
                                        .build());

        if (props.isEnableTaskList()) {
            builder.enableTaskList();
        }

        var fallback = ContentCreationModelFactory.buildFallback(props);
        if (fallback != null) {
            builder.fallbackModel(fallback);
        }

        applySandbox(builder, props);
        registerInfrastructure(builder, props, services, baseStore, sessionsTool);

        log.info(
                "[ContentCreation] 主 Agent 构建完成 agentId={} workspace={} sandbox={} hasStore={}"
                        + " hasSessionsTool={}",
                props.getMainAgentId(),
                workspaceDir,
                props.getSandboxType(),
                baseStore != null,
                sessionsTool != null);
        return builder.build();
    }

    /** 构建编辑子 Agent（agentId=editor），由主 Agent 通过 spawn_subagent 派发，或独立通过 AG-UI 端点直连。 */
    public static HarnessAgent createEditorAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            BaseStore baseStore,
            AiModel resolvedModel,
            AiProperties aiProperties) {
        var workspaceDir = props.getWorkspaceRoot().toAbsolutePath().toString();
        var toolkit = new Toolkit();
        // editor 子 agent 工具池更小：只能查 KB 求证，不能审批、不能写长期记忆
        toolkit.registerTool(
                new KnowledgeSearchTool(services.kbSearch(), services.embeddingService()));

        var model =
                resolvedModel != null
                        ? ContentCreationModelFactory.buildFromAiModel(resolvedModel, aiProperties)
                        : ContentCreationModelFactory.build(props);

        var builder =
                HarnessAgent.builder()
                        .agentId(props.getEditorAgentId())
                        .name("AAF 内容编辑助理")
                        .description("二次审稿子 Agent，由内容创作主 Agent 派发，专注语气统一/错别字/事实核查")
                        .model(model)
                        .sysPrompt(EditorSystemPrompt.build(workspaceDir))
                        .workspace(props.getWorkspaceRoot())
                        .toolkit(toolkit)
                        .maxIters(props.getEditorMaxIterations())
                        .compaction(
                                CompactionConfig.builder()
                                        .triggerMessages(props.getCompactionTriggerMessages())
                                        .keepMessages(props.getCompactionKeepMessages())
                                        .flushBeforeCompact(true)
                                        .build());

        applySandbox(builder, props);
        // editor 不获得 SessionsTool，避免子 agent 嵌套派发
        registerInfrastructure(builder, props, services, baseStore, null);

        log.info("[ContentCreation] 编辑子 Agent 构建完成 agentId={}", props.getEditorAgentId());
        return builder.build();
    }

    /**
     * 构建客服 Agent（agentId=customer-service）。
     *
     * <p>只注册 {@code search_kb} 和 {@code switch_kb}，专注知识库检索回答，未登录用户默认路由到此。
     */
    public static HarnessAgent createCustomerServiceAgent(
            ContentCreationProperties props,
            AafAgentServices services,
            BaseStore baseStore,
            AiModel resolvedModel,
            String assistantPrompt,
            String kbContext,
            AiProperties aiProperties) {
        var toolkit = new Toolkit();
        toolkit.registerTool(new KnowledgeSearchTool(services.kbSearch(), services.embeddingService()));
        toolkit.registerTool(new SwitchKbTool(services.jdbcTemplate()));

        var model = resolvedModel != null
                ? ContentCreationModelFactory.buildFromAiModel(resolvedModel, aiProperties)
                : ContentCreationModelFactory.build(props);

        var sysPrompt = buildCustomerServicePrompt(assistantPrompt, kbContext);

        var builder = HarnessAgent.builder()
                .agentId("customer-service")
                .name("AAF 客服助理")
                .description("面向未登录用户的客服 Agent，基于知识库回答产品咨询")
                .model(model)
                .sysPrompt(sysPrompt)
                .workspace(props.getWorkspaceRoot())
                .toolkit(toolkit)
                .maxIters(20);

        applySandbox(builder, props);
        registerInfrastructure(builder, props, services, baseStore, null);

        log.info("[ContentCreation] 客服 Agent 构建完成 agentId=customer-service");
        return builder.build();
    }

    private static String buildCustomerServicePrompt(String assistantPrompt, String kbContext) {
        var sb = new StringBuilder(1024);
        sb.append("你是 AAF 平台的客服助理，负责解答用户关于产品的咨询问题。\n\n");
        sb.append("# 服务原则\n");
        sb.append("- 优先基于知识库内容回答，调用 `search_kb` 检索相关资料后再作答\n");
        sb.append("- 知识库中没有的内容，如实告知并建议联系人工客服\n");
        sb.append("- 保持友好、简洁、专业的语气\n");
        sb.append("- 不得捏造产品功能或价格信息\n\n");
        sb.append("# 工具使用\n");
        sb.append("- `search_kb(query, topK)`：检索产品知识库，回答前先搜索相关内容\n");
        sb.append("- `switch_kb(knowledgeBaseId)`：切换到其他知识库（如有需要）\n\n");
        if (kbContext != null && !kbContext.isBlank()) {
            sb.append(kbContext);
        }
        if (assistantPrompt != null && !assistantPrompt.isBlank()) {
            sb.append(assistantPrompt);
        }
        return sb.toString();
    }

    private static void applySandbox(
            HarnessAgent.Builder builder, ContentCreationProperties props) {
        if ("docker".equalsIgnoreCase(props.getSandboxType())) {
            var spec = new DockerFilesystemSpec();
            spec.image(props.getSandboxImage());
            spec.workspaceRoot("/home/agentscope/workspace");
            spec.isolationScope(IsolationScope.SESSION);
            builder.filesystem(spec);
        }
        // sandboxType=none 时不调 .filesystem(...)，沿用默认本地文件系统
    }

    private static void registerInfrastructure(
            HarnessAgent.Builder builder,
            ContentCreationProperties props,
            AafAgentServices services,
            BaseStore baseStore,
            SessionsTool sessionsTool) {
        // SessionsTool：让主 Agent 能 spawn_subagent
        if (sessionsTool != null) {
            builder.externalSubagentTool(sessionsTool);
        }

        // FileProcessingMiddleware：将消息中的文件 URL 按类型转换（图片→ImageBlock，文档→解析文本）
        if (services != null && services.importerFactory() != null) {
            builder.middleware(new FileProcessingMiddleware(services.importerFactory()));
        }

        // ConversationBridgeMiddleware：把每轮决策同步写到 conversation_message 表
        if (services != null && services.jdbcTemplate() != null) {
            builder.middleware(new ConversationBridgeMiddleware(services.jdbcTemplate()));
            // CallLogMiddleware：token 粒度记录 LLM 调用与工具调用日志，并按 token 结算积分
            builder.middleware(
                    new CallLogMiddleware(
                            services.jdbcTemplate(),
                            services.creditGuard(),
                            services.modelRepository()));
        }

        // ThinkingMiddleware：按对话（per-thread）动态开启思考模式
        builder.middleware(new ThinkingMiddleware());

        // UiEventMiddleware：检测 send_ui 工具结果，发出 CustomEvent(ui_block) 到 SSE 流
        builder.middleware(new UiEventMiddleware());

        // MessageQueueMiddleware：thread 忙时把新消息排队，下次推理前注入到系统提示词
        if (baseStore != null) {
            builder.middleware(new MessageQueueMiddleware(baseStore));
        }

        // ThreadBudgetMiddleware：单 thread 模型调用上限（防失控）
        builder.middleware(new ThreadBudgetMiddleware(props.getThreadModelCallBudget()));

        // ModelCallLimitMiddleware：全局模型调用上限（跨所有 thread）
        builder.middleware(new ModelCallLimitMiddleware(props.getGlobalModelCallLimit()));
    }
}
