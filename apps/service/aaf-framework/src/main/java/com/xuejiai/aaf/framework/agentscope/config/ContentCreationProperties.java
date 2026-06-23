/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AAF 内容创作 Agent 配置项。
 *
 * <p>所有字段都有合理默认值，除非环境特殊需求否则无需在 {@code application.yml} 中显式声明。
 */
@ConfigurationProperties(prefix = "aaf.agentscope.assistant")
public class ContentCreationProperties {

    /** 是否启用内容创作 Agent 自动装配。 */
    private boolean enabled = true;

    /**
     * 工作区根目录。每个 thread 在此目录下有独立子目录（{@code <root>/<threadId>/}）， 用于存放 skills、subagents、生成内容快照等。默认
     * {@code ~/.aaf/agentscope/content-creation/workspace}。
     */
    private Path workspaceRoot =
            Paths.get(
                    System.getProperty("user.home"),
                    ".aaf",
                    "agentscope",
                    "content-creation",
                    "workspace");

    /** 主 Agent ID，AG-UI 端点路径 {@code /agui/run/{agentId}} 时使用。 */
    private String mainAgentId = "assistant";

    /** 编辑/校对子 Agent ID。 */
    private String editorAgentId = "editor";

    /** 主 Agent 最大推理迭代轮数。 */
    private int maxIterations = 50;

    /** 编辑子 Agent 最大推理迭代轮数（一般少于主 Agent）。 */
    private int editorMaxIterations = 20;

    /**
     * 模型 ID（格式：{@code dashscope:qwen-max} / {@code openai:gpt-4o} / {@code
     * anthropic:claude-3-5-sonnet}）。
     */
    private String modelId = "dashscope:qwen-max";

    /** 备用模型 ID（主模型限流/失败时自动切换）。空字符串 = 不启用降级。 */
    private String fallbackModelId = "";

    /** 沙箱类型：{@code none}=本地文件系统 / {@code docker}=每个 session 一个 Docker 容器。 */
    private String sandboxType = "none";

    /** Docker 沙箱镜像（{@code sandboxType=docker} 时生效）。 */
    private String sandboxImage = "agentscope/coding-sandbox:latest";

    /** 单 thread 模型调用预算（防失控）。 */
    private int threadModelCallBudget = 200;

    /** 全局模型调用上限（跨所有 thread）。 */
    private int globalModelCallLimit = 5000;

    /** 上下文压缩触发阈值（消息条数）。 */
    private int compactionTriggerMessages = 40;

    /** 上下文压缩后保留的近期消息条数。 */
    private int compactionKeepMessages = 15;

    /** 是否启用 enableTaskList 计划模式（自带 todo_write 工具 + 持久化提醒）。 */
    private boolean enableTaskList = true;

    /**
     * 开发态兜底 user_id——当 AG-UI 请求未在 forwardedProps 里传 userId 时使用。
     *
     * <p>默认 null（生产模式）。设为非空时，所有 AG-UI 调用都视作此 user 发起，方便 dev 用 curl/Postman 调试工具实现。
     *
     * <p>⚠️ 生产环境**必须**留 null，由真实用户身份解析层填充上下文（详见 {@code AafContextHolder} 的 TODO）。
     */
    private Long devModeUserId;

    /** 开发态兜底 conversation_id——配合 {@link #devModeUserId} 使用，让消息持久化跑得起来。 */
    private Long devModeConversationId;

    /** 开发态兜底 knowledge_base_id——配合 {@link #devModeUserId} 使用，让 search_kb 跑得起来。 */
    private Long devModeKnowledgeBaseId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getMainAgentId() {
        return mainAgentId;
    }

    public void setMainAgentId(String mainAgentId) {
        this.mainAgentId = mainAgentId;
    }

    public String getEditorAgentId() {
        return editorAgentId;
    }

    public void setEditorAgentId(String editorAgentId) {
        this.editorAgentId = editorAgentId;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getEditorMaxIterations() {
        return editorMaxIterations;
    }

    public void setEditorMaxIterations(int editorMaxIterations) {
        this.editorMaxIterations = editorMaxIterations;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getFallbackModelId() {
        return fallbackModelId;
    }

    public void setFallbackModelId(String fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }

    public String getSandboxType() {
        return sandboxType;
    }

    public void setSandboxType(String sandboxType) {
        this.sandboxType = sandboxType;
    }

    public String getSandboxImage() {
        return sandboxImage;
    }

    public void setSandboxImage(String sandboxImage) {
        this.sandboxImage = sandboxImage;
    }

    public int getThreadModelCallBudget() {
        return threadModelCallBudget;
    }

    public void setThreadModelCallBudget(int threadModelCallBudget) {
        this.threadModelCallBudget = threadModelCallBudget;
    }

    public int getGlobalModelCallLimit() {
        return globalModelCallLimit;
    }

    public void setGlobalModelCallLimit(int globalModelCallLimit) {
        this.globalModelCallLimit = globalModelCallLimit;
    }

    public int getCompactionTriggerMessages() {
        return compactionTriggerMessages;
    }

    public void setCompactionTriggerMessages(int compactionTriggerMessages) {
        this.compactionTriggerMessages = compactionTriggerMessages;
    }

    public int getCompactionKeepMessages() {
        return compactionKeepMessages;
    }

    public void setCompactionKeepMessages(int compactionKeepMessages) {
        this.compactionKeepMessages = compactionKeepMessages;
    }

    public boolean isEnableTaskList() {
        return enableTaskList;
    }

    public void setEnableTaskList(boolean enableTaskList) {
        this.enableTaskList = enableTaskList;
    }

    public Long getDevModeUserId() {
        return devModeUserId;
    }

    public void setDevModeUserId(Long devModeUserId) {
        this.devModeUserId = devModeUserId;
    }

    public Long getDevModeConversationId() {
        return devModeConversationId;
    }

    public void setDevModeConversationId(Long devModeConversationId) {
        this.devModeConversationId = devModeConversationId;
    }

    public Long getDevModeKnowledgeBaseId() {
        return devModeKnowledgeBaseId;
    }

    public void setDevModeKnowledgeBaseId(Long devModeKnowledgeBaseId) {
        this.devModeKnowledgeBaseId = devModeKnowledgeBaseId;
    }
}
