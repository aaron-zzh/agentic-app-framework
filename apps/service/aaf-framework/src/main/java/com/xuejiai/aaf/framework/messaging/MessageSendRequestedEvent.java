package com.xuejiai.aaf.framework.messaging;

import com.xuejiai.aaf.framework.messaging.MessageTemplateProvider.MessageTemplateInfo;

/**
 * 消息发送请求事件，由 MessageServiceImpl 发布，MessageSendListener 异步监听执行实际发送。
 *
 * @param request 原始消息请求
 * @param templateInfo 已解析的模板信息（含渲染后 content）
 * @param renderedContent 渲染后的内容
 * @param subject 最终主题
 * @param logId 预写日志 ID（用于发送完成后更新状态）
 */
public record MessageSendRequestedEvent(
        MessageRequest request,
        MessageTemplateInfo templateInfo,
        String renderedContent,
        String subject,
        Long logId) {}
