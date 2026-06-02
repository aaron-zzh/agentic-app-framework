/**
 * AG-UI 对外协议入口——统一 AI 对话入口（助理对话走 {@code /agui/runs}）。
 *
 * <p>从 {@code module.ai.chat.agui} 提升为顶层：AG-UI 是统一 AI 入口，不再是 chat 的子能力。
 *
 * <p>计划内容（仅 AgentScope {@code /agui/runs} 链路）：AafAguiRestController、AafAgentResolver、
 * AafAguiConfirmController、AafAguiRegistryCustomizer、AafAguiConfiguration、ChatSessionResolverImpl。
 *
 * <p>注：AgUiEvent / AgUiStreamHandler / AgentRunEventStreamService 是被 Spring AI 直连链路、 工作流、用户聊天共享的
 * AG-UI 事件工具，不属本链路，保留原处。
 */
package com.xuejiai.aaf.module.ai.agui;
