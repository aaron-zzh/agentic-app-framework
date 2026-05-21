package com.xuejiai.aaf.module.system.sms.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.messaging.sms.SmsRateLimiter;
import com.xuejiai.aaf.framework.messaging.sms.SmsSendEvent;
import com.xuejiai.aaf.framework.messaging.sms.SmsSenderRouter;
import com.xuejiai.aaf.module.system.sms.domain.SmsTemplate;
import com.xuejiai.aaf.module.system.sms.repository.SmsTemplateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 短信发送服务。
 *
 * <p>直接使用厂商模板 ID 发送，不经过 FreeMarker 渲染。 签名和模板 ID 从 sys_sms_template 表读取，支持按模板指定厂商。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final SmsTemplateRepository templateRepository;
    private final SmsSenderRouter smsSenderRouter;
    private final SmsRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 按业务场景编码发送短信。
     *
     * @param phone 手机号
     * @param code 业务场景编码（对应 sys_sms_template.code）
     * @param params 模板变量
     */
    public void send(String phone, String code, Map<String, String> params) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("手机号格式不正确: " + phone);
        }
        var template =
                templateRepository
                        .findByCodeAndStatusAndDeletedFalse(code, (short) 1)
                        .orElseThrow(() -> new IllegalArgumentException("短信模板不存在或已禁用: " + code));

        rateLimiter.check(phone);
        doSend(phone, template, params);
    }

    private void doSend(String phone, SmsTemplate template, Map<String, String> params) {
        var sendTime = LocalDateTime.now();
        boolean success = false;
        String apiCode = null, apiMsg = null;
        var provider = template.getProvider();
        try {
            smsSenderRouter.sendWith(provider, phone, template.getApiTemplateId(), params);
            success = true;
            apiCode = "OK";
        } catch (Exception e) {
            apiMsg = e.getMessage();
            log.error("短信发送失败: phone={}, code={}", phone, template.getCode(), e);
            throw new RuntimeException("短信发送失败", e);
        } finally {
            eventPublisher.publishEvent(
                    new SmsSendEvent(
                            phone,
                            template.getApiTemplateId(),
                            params,
                            provider != null ? provider : "default",
                            success,
                            sendTime,
                            null,
                            apiCode,
                            apiMsg));
        }
    }
}
