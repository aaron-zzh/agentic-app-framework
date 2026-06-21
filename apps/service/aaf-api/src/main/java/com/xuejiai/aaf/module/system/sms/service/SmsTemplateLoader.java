package com.xuejiai.aaf.module.system.sms.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.messaging.sms.SmsTemplateProvider;
import com.xuejiai.aaf.module.system.sms.repository.SmsTemplateRepository;

import lombok.RequiredArgsConstructor;

/**
 * 短信模板加载器：从 sys_sms_template 加载启用状态的模板，提供给 SmsChannelSender 使用。
 *
 * <p>仅做查询，不持有任何业务逻辑——业务层模板 CRUD 由 SmsController 直接调 SmsTemplateRepository 完成。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class SmsTemplateLoader implements SmsTemplateProvider {

    private static final short STATUS_ENABLED = 1;

    private final SmsTemplateRepository repository;

    @Override
    public Optional<SmsTemplateInfo> findByCode(String code) {
        return repository
                .findByCodeAndStatusAndDeletedFalse(code, STATUS_ENABLED)
                .map(t -> new SmsTemplateInfo(t.getCode(), t.getApiTemplateId(), t.getProvider()));
    }
}
