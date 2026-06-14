package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;

/**
 * 消息发送请求。
 *
 * @param channel 消息渠道
 * @param templateCode 模板编码（与 content 二选一，优先模板）
 * @param recipients 接收人列表（手机号/邮箱/用户ID）
 * @param variables 模板变量
 * @param subject 消息主题（邮件标题等）
 * @param content 直接内容（不使用模板时传入，与 templateCode 二选一）
 */
public record MessageRequest(
        MessageChannel channel,
        String templateCode,
        List<String> recipients,
        Map<String, Object> variables,
        String subject,
        String content) {

    /** 使用模板发送 */
    public MessageRequest(
            MessageChannel channel,
            String templateCode,
            List<String> recipients,
            Map<String, Object> variables,
            String subject) {
        this(channel, templateCode, recipients, variables, subject, null);
    }

    /** 直接发送（不使用模板） */
    public static MessageRequest direct(
            MessageChannel channel, String subject, String content, List<String> recipients) {
        return new MessageRequest(channel, null, recipients, Map.of(), subject, content);
    }
}
