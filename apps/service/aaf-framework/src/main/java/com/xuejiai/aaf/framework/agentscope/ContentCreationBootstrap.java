/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.agentscope.config.ContentCreationProperties;
import com.xuejiai.aaf.framework.agentscope.gateway.HarnessGateway;
import com.xuejiai.aaf.framework.agentscope.session.AgentManagerConfig;
import com.xuejiai.aaf.framework.agentscope.session.SessionAgentManager;
import com.xuejiai.aaf.framework.agentscope.session.SessionStore;
import com.xuejiai.aaf.framework.agentscope.session.SubagentRunRegistry;
import com.xuejiai.aaf.framework.agentscope.session.tool.SessionsTool;
import com.xuejiai.aaf.framework.agentscope.store.SqliteBaseStore;

import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.subagent.DefaultAgentManager;
import io.agentscope.harness.agent.subagent.task.TaskRepository;
import io.agentscope.harness.agent.subagent.task.WorkspaceTaskRepository;
import io.agentscope.harness.agent.workspace.WorkspaceManager;

/**
 * 内容创作 Agent 基础设施装配器——本类是 {@code CodingBootstrap} 的简化版。
 *
 * <p>装配产物（{@link Infrastructure}）只包含 <b>singleton 基础设施</b>：BaseStore / WorkspaceManager /
 * SessionStore / SessionAgentManager / ChannelManager / HarnessGateway / SessionsTool /
 * TaskRepository。
 *
 * <p>{@code HarnessAgent 实例本身}由 {@code ContentCreationAutoConfiguration} 用 prototype scope 的 @Bean
 * 工厂方法按需创建——这样每个 AG-UI threadId 都能拿到独立的 agent 实例（多用户隔离）。
 */
public final class ContentCreationBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ContentCreationBootstrap.class);

    private ContentCreationBootstrap() {}

    /**
     * 装配 singleton 基础设施。
     *
     * @param props 配置
     * @return {@link Infrastructure} 持有可独立暴露为 Spring Bean 的对象
     */
    public static Infrastructure assemble(ContentCreationProperties props) {
        Objects.requireNonNull(props, "props");

        Path workspace = props.getWorkspaceRoot().toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 workspace 目录: " + workspace, e);
        }

        // 0. 把 classpath 下 workspace-templates/* 复制到 workspace（已存在则跳过，不覆盖用户编辑）
        seedWorkspaceTemplates(workspace);

        // 1. BaseStore（sqlite，文件位于 workspace/data.db）
        Path dbPath = workspace.resolve("data.db");
        BaseStore baseStore;
        try {
            baseStore = new SqliteBaseStore(dbPath.toString());
            log.info("[ContentCreation] BaseStore 启动: {}", dbPath);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化 SqliteBaseStore 失败: " + dbPath, e);
        }

        // 2. WorkspaceManager + DefaultAgentManager（subagent entries 暂为空，待 workspace/subagents/*.md
        // 加载）
        WorkspaceManager wsManager = new WorkspaceManager(workspace);
        DefaultAgentManager dam = new DefaultAgentManager(List.of(), wsManager);

        // 3. SessionStore（持久化 sessions.json）
        Path sessionsFile = workspace.resolve("sessions.json");
        SessionStore sessionStore = new SessionStore(sessionsFile);
        sessionStore.load();
        log.info("[ContentCreation] SessionStore 启动: {}", sessionsFile);

        // 4. SessionAgentManager + SubagentRunRegistry
        SubagentRunRegistry runRegistry = new SubagentRunRegistry();
        SessionAgentManager sessionAgentManager =
                new SessionAgentManager(
                        dam, AgentManagerConfig.defaults(), runRegistry, sessionStore);

        // 5. ChannelManager + HarnessGateway（IM 通道扩展用，AG-UI 链路本身不依赖）
        ChannelManager channelManager = new ChannelManager();
        HarnessGateway gateway = HarnessGateway.create(sessionAgentManager, channelManager);

        // 6. TaskRepository + SessionsTool（让主 Agent 能 spawn_subagent）
        TaskRepository taskRepo = new WorkspaceTaskRepository(wsManager, props.getMainAgentId());
        SessionsTool sessionsTool = new SessionsTool(sessionAgentManager, taskRepo, null, 0);

        log.info(
                "[ContentCreation] 基础设施装配完成 workspace={} mainAgentId={} editorAgentId={}",
                workspace,
                props.getMainAgentId(),
                props.getEditorAgentId());

        return new Infrastructure(
                baseStore,
                wsManager,
                sessionStore,
                sessionAgentManager,
                channelManager,
                gateway,
                sessionsTool);
    }

    /** 装配产物——所有 singleton 可暴露为 Spring Bean 的组件。Agent 本身不在内（prototype 在 AutoConfiguration）。 */
    public record Infrastructure(
            BaseStore baseStore,
            WorkspaceManager workspaceManager,
            SessionStore sessionStore,
            SessionAgentManager sessionAgentManager,
            ChannelManager channelManager,
            HarnessGateway harnessGateway,
            SessionsTool sessionsTool) {}

    // -----------------------------------------------------------------
    //  Workspace template seeding
    // -----------------------------------------------------------------

    /**
     * Bundled workspace templates seeded into the agent workspace on first run:
     *
     * <ul>
     *   <li>内容创作 skills（自动从 {@code <workspace>/skills/} 加载）
     *   <li>子 Agent 声明（从 {@code <workspace>/subagents/} 扫描）
     * </ul>
     */
    private static final List<String> WORKSPACE_TEMPLATE_RESOURCES =
            List.of(
                    "skills/outline-build/SKILL.md",
                    "skills/tone-tune/SKILL.md",
                    "skills/fact-check/SKILL.md",
                    "skills/seo-optimize/SKILL.md",
                    "subagents/editor.md",
                    "subagents/researcher.md");

    /** 把 classpath {@code /workspace-templates/} 资源拷到工作区。已存在则跳过——尊重用户编辑。 */
    private static void seedWorkspaceTemplates(Path workspace) {
        for (String rel : WORKSPACE_TEMPLATE_RESOURCES) {
            Path target = workspace.resolve(rel).normalize();
            if (Files.exists(target)) {
                continue;
            }
            String resource = "/workspace-templates/" + rel;
            try (InputStream is = ContentCreationBootstrap.class.getResourceAsStream(resource)) {
                if (is == null) {
                    log.debug("[ContentCreation] 模板资源不存在: {}", resource);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(is, target);
                log.info("[ContentCreation] Seeded workspace template {} -> {}", rel, target);
            } catch (IOException e) {
                log.warn("[ContentCreation] 模板拷贝失败 {} -> {}: {}", resource, target, e.getMessage());
            }
        }
    }
}
