package com.xuejiai.aaf.module.livechat.service;

import com.xuejiai.aaf.module.livechat.domain.ChatSession;

/**
 * 会话关闭事件。
 *
 * <p>会话关闭后发布，用于触发评价邀请等后续流程。
 */
public record SessionClosedEvent(ChatSession session) {}
