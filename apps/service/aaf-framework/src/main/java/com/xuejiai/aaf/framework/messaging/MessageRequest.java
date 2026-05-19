package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;

/**
 * 消息发送请求。
 *
 * @param channel 消息渠道
 * @param templateCode 模板编码
 * @param recipients 接收人列表（手机号/邮箱/用户ID）
 * @param variables 模板变量
 * @param subject 邮件主题（仅 EMAIL 渠道使用）
 */
public record MessageRequest(
        MessageChannel channel,
        String templateCode,
        List<String> recipients,
        Map<String, Object> variables,
        String subject) {}
