package com.xuejiai.aaf.module.system.entity.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.ErrorCodeConstants;
import com.xuejiai.aaf.module.system.entity.domain.RecordTemplate;
import com.xuejiai.aaf.module.system.entity.repository.RecordTemplateRepository;
import com.xuejiai.aaf.module.system.entity.vo.RecordTemplateCreateDTO;
import com.xuejiai.aaf.module.system.entity.vo.RecordTemplateVO;

import lombok.RequiredArgsConstructor;

/**
 * 记录模板业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordTemplateService {

    private final RecordTemplateRepository recordTemplateRepository;
    private final OperatorContext operatorContext;

    /** 查询某实体下当前用户可见的模板（自己的 + 共享的） */
    public List<RecordTemplateVO> listBySlug(String entitySlug) {
        var ownerId = currentOwnerId();
        return recordTemplateRepository
                .findByEntitySlugAndCreateByOrEntitySlugAndIsSharedTrue(
                        entitySlug, ownerId, entitySlug)
                .stream()
                .map(this::toVO)
                .toList();
    }

    /** 创建模板 */
    @Transactional
    public RecordTemplateVO create(RecordTemplateCreateDTO dto) {
        var entity = new RecordTemplate();
        entity.setEntitySlug(dto.entitySlug());
        entity.setName(dto.name());
        entity.setFieldValues(dto.fieldValues());
        entity.setIsShared(dto.isShared() != null && dto.isShared());
        entity.setIsDefault(dto.isDefault() != null && dto.isDefault());
        var ownerId = currentOwnerId();
        entity.setCreateBy(ownerId);
        entity.setOwnerId(ownerId);
        return toVO(recordTemplateRepository.save(entity));
    }

    /** 更新模板 */
    @Transactional
    public RecordTemplateVO update(Long id, RecordTemplateCreateDTO dto) {
        var entity = findOwnedById(id);
        entity.setName(dto.name());
        entity.setFieldValues(dto.fieldValues());
        entity.setIsShared(dto.isShared() != null && dto.isShared());
        return toVO(recordTemplateRepository.save(entity));
    }

    /** 复制模板 */
    @Transactional
    public RecordTemplateVO copy(Long id) {
        var source = findReadableById(id);
        var copy = new RecordTemplate();
        copy.setEntitySlug(source.getEntitySlug());
        copy.setName(source.getName() + " (副本)");
        copy.setFieldValues(source.getFieldValues());
        copy.setIsShared(false);
        copy.setIsDefault(false);
        var ownerId = currentOwnerId();
        copy.setCreateBy(ownerId);
        copy.setOwnerId(ownerId);
        return toVO(recordTemplateRepository.save(copy));
    }

    /** 设为默认模板 */
    @Transactional
    public void setDefault(Long id) {
        var ownerId = currentOwnerId();
        var entity = findOwnedById(id);
        recordTemplateRepository.clearDefault(entity.getEntitySlug(), ownerId);
        entity.setIsDefault(true);
        recordTemplateRepository.save(entity);
    }

    /** 删除模板 */
    @Transactional
    public void delete(Long id) {
        recordTemplateRepository.delete(findOwnedById(id));
    }

    private RecordTemplate findOwnedById(Long id) {
        return recordTemplateRepository
                .findByIdAndCreateBy(id, currentOwnerId())
                .orElseThrow(() -> exception(ErrorCodeConstants.RECORD_TEMPLATE_NOT_FOUND));
    }

    private RecordTemplate findReadableById(Long id) {
        var entity =
                recordTemplateRepository
                        .findById(id)
                        .orElseThrow(
                                () -> exception(ErrorCodeConstants.RECORD_TEMPLATE_NOT_FOUND));
        if (!java.util.Objects.equals(entity.getCreateBy(), currentOwnerId())
                && !Boolean.TRUE.equals(entity.getIsShared())) {
            throw exception(ErrorCodeConstants.RECORD_TEMPLATE_NOT_FOUND);
        }
        return entity;
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED));
    }

    private RecordTemplateVO toVO(RecordTemplate e) {
        return new RecordTemplateVO(
                e.getId(),
                e.getEntitySlug(),
                e.getName(),
                e.getFieldValues(),
                e.getIsShared(),
                e.getIsDefault(),
                e.getCreateBy(),
                e.getCreateTime());
    }
}
