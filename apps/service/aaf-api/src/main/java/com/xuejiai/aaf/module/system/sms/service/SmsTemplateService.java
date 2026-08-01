package com.xuejiai.aaf.module.system.sms.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.SMS_TEMPLATE_NOT_FOUND;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.sms.domain.SmsTemplate;
import com.xuejiai.aaf.module.system.sms.repository.SmsTemplateRepository;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateCreateDTO;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateUpdateDTO;
import com.xuejiai.aaf.module.system.sms.vo.SmsTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 短信模板管理业务逻辑。
 *
 * <p>M22：原实现由 SmsController 直接注入 SmsTemplateRepository 并操作，违反
 * "controller→service→repository" 分层，且直接返回实体。迁移到独立 service 层，出参改 VO，
 * 与 MessageTemplateService（notify 模块同类模板管理）同一模式。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class SmsTemplateService {

    private final SmsTemplateRepository repository;

    /** 列表查询 */
    public List<SmsTemplateVO> list() {
        return repository.findAll().stream().map(this::toVO).toList();
    }

    /** 详情 */
    public SmsTemplateVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 创建 */
    @Transactional
    public SmsTemplateVO create(SmsTemplateCreateDTO dto) {
        var entity = new SmsTemplate();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setSignName(dto.signName());
        entity.setApiTemplateId(dto.apiTemplateId());
        entity.setParams(dto.params());
        entity.setProvider(dto.provider());
        entity.setStatus((short) 1);
        return toVO(repository.save(entity));
    }

    /** 更新 */
    @Transactional
    public SmsTemplateVO update(Long id, SmsTemplateUpdateDTO dto) {
        var entity = findById(id);
        if (dto.signName() != null) entity.setSignName(dto.signName());
        if (dto.apiTemplateId() != null) entity.setApiTemplateId(dto.apiTemplateId());
        if (dto.provider() != null) entity.setProvider(dto.provider());
        if (dto.status() != null) entity.setStatus(dto.status());
        return toVO(repository.save(entity));
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private SmsTemplate findById(Long id) {
        return repository.findById(id).orElseThrow(() -> exception(SMS_TEMPLATE_NOT_FOUND));
    }

    private SmsTemplateVO toVO(SmsTemplate t) {
        return new SmsTemplateVO(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getSignName(),
                t.getApiTemplateId(),
                t.getParams(),
                t.getProvider(),
                t.getStatus(),
                t.getCreateTime(),
                t.getUpdateTime());
    }
}
