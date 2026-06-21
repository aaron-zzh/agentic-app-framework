package com.xuejiai.aaf.framework.messaging.sms;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xuejiai.aaf.framework.messaging.ChannelSender;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 短信渠道发送器，统一收口短信发送流程。
 *
 * <p>实现要点：
 *
 * <ul>
 *   <li><b>subject 复用为 templateCode</b>：MessageService 对 SMS 渠道跳过模板渲染， 将业务侧传入的 templateCode 透传到
 *       subject 参数（SMS 不需要本地渲染内容）
 *   <li><b>模板解析</b>：用 templateCode 查 sys_sms_template，拿到 api_template_id + provider
 *   <li><b>限频</b>：在调用厂商 API 前对每个手机号做频率检查
 *   <li><b>厂商路由</b>：模板指定 provider 优先；未指定时退回系统默认 provider
 * </ul>
 *
 * <p>批量发送时一旦其中某号码失败立即抛出，已发出的不回滚（短信本身不支持事务回滚）。
 */
@Slf4j
@RequiredArgsConstructor
public class SmsChannelSender implements ChannelSender {

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsTemplateProvider templateProvider;
    private final SmsSenderRouter smsSenderRouter;
    private final SmsRateLimiter rateLimiter;

    @Override
    public MessageChannel channel() {
        return MessageChannel.SMS;
    }

    @Override
    public ProviderResponse send(
            List<String> recipients,
            String subject,
            String content,
            Map<String, Object> variables) {
        // subject 承载 templateCode（SMS 渠道约定，由 MessageService 透传）
        var templateCode = subject;
        if (templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("短信发送缺少模板编码（subject 字段）");
        }
        var template =
                templateProvider
                        .findByCode(templateCode)
                        .orElseThrow(
                                () -> new IllegalArgumentException("短信模板不存在或已禁用: " + templateCode));

        var params =
                variables == null
                        ? Map.<String, String>of()
                        : variables.entrySet().stream()
                                .collect(
                                        Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> String.valueOf(e.getValue())));

        ProviderResponse lastResponse = ProviderResponse.empty();
        for (var phone : recipients) {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                log.warn("手机号格式不正确，跳过发送: {}", phone);
                continue;
            }
            rateLimiter.check(phone);
            lastResponse =
                    smsSenderRouter.sendWith(
                            template.provider(), phone, template.apiTemplateId(), params);
        }
        return lastResponse;
    }
}
