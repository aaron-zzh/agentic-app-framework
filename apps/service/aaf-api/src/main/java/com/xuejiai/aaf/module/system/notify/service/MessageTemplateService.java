package com.xuejiai.aaf.module.system.notify.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageTemplateEngine;
import com.xuejiai.aaf.framework.messaging.MessageTemplateProvider;
import com.xuejiai.aaf.module.system.notify.domain.MessageTemplate;
import com.xuejiai.aaf.module.system.notify.repository.MessageTemplateRepository;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateCreateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateUpdateDTO;
import com.xuejiai.aaf.module.system.notify.vo.MessageTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 消息模板业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class MessageTemplateService implements MessageTemplateProvider {

    private final MessageTemplateRepository repository;
    private final MessageTemplateEngine templateEngine;

    /** 列表查询 */
    public List<MessageTemplateVO> list() {
        return repository.findAll().stream().map(this::toVO).toList();
    }

    /** 详情 */
    public MessageTemplateVO getById(Long id) {
        return toVO(findById(id));
    }

    /** 创建 */
    @Transactional
    public MessageTemplateVO create(MessageTemplateCreateDTO dto) {
        var entity = new MessageTemplate();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setChannel(dto.channel());
        entity.setSubject(dto.subject());
        entity.setContent(dto.content());
        entity.setVariables(dto.variables());
        entity.setStatus(dto.status());
        return toVO(repository.save(entity));
    }

    /** 更新 */
    @Transactional
    public MessageTemplateVO update(Long id, MessageTemplateUpdateDTO dto) {
        var entity = findById(id);
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.channel() != null) entity.setChannel(dto.channel());
        if (dto.subject() != null) entity.setSubject(dto.subject());
        if (dto.content() != null) entity.setContent(dto.content());
        if (dto.variables() != null) entity.setVariables(dto.variables());
        if (dto.status() != null) entity.setStatus(dto.status());
        return toVO(repository.save(entity));
    }

    /** 删除 */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 预览渲染 */
    public String preview(Long id, Map<String, Object> variables) {
        var entity = findById(id);
        return templateEngine.render(entity.getContent(), variables);
    }

    /** 实现 MessageTemplateProvider 接口，供 MessageServiceImpl 使用 */
    @Override
    public Optional<MessageTemplateInfo> findByCode(String code) {
        return repository
                .findByCodeAndDeletedFalse(code)
                .map(
                        t ->
                                new MessageTemplateInfo(
                                        t.getCode(),
                                        MessageChannel.valueOf(t.getChannel()),
                                        t.getSubject(),
                                        t.getContent()));
    }

    private MessageTemplate findById(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "消息模板不存在"));
    }

    private MessageTemplateVO toVO(MessageTemplate t) {
        return new MessageTemplateVO(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getChannel(),
                t.getSubject(),
                t.getContent(),
                t.getVariables(),
                t.getStatus(),
                t.getCreateTime(),
                t.getUpdateTime());
    }
}
