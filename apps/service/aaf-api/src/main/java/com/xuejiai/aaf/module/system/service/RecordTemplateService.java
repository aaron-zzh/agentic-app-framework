package com.xuejiai.aaf.module.system.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.RecordTemplate;
import com.xuejiai.aaf.module.system.repository.RecordTemplateRepository;
import com.xuejiai.aaf.module.system.vo.RecordTemplateCreateDTO;
import com.xuejiai.aaf.module.system.vo.RecordTemplateVO;

import lombok.RequiredArgsConstructor;

/** 记录模板业务逻辑。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordTemplateService {

    private final RecordTemplateRepository recordTemplateRepository;

    /** 查询某实体下当前用户可见的模板（自己的 + 共享的） */
    public List<RecordTemplateVO> listBySlug(String entitySlug, Long userId) {
        return recordTemplateRepository
                .findByEntitySlugAndCreateByOrEntitySlugAndIsSharedTrue(
                        entitySlug, userId, entitySlug)
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
        return toVO(recordTemplateRepository.save(entity));
    }

    /** 更新模板 */
    @Transactional
    public RecordTemplateVO update(Long id, RecordTemplateCreateDTO dto) {
        var entity = findById(id);
        entity.setName(dto.name());
        entity.setFieldValues(dto.fieldValues());
        entity.setIsShared(dto.isShared() != null && dto.isShared());
        return toVO(recordTemplateRepository.save(entity));
    }

    /** 复制模板 */
    @Transactional
    public RecordTemplateVO copy(Long id) {
        var source = findById(id);
        var copy = new RecordTemplate();
        copy.setEntitySlug(source.getEntitySlug());
        copy.setName(source.getName() + " (副本)");
        copy.setFieldValues(source.getFieldValues());
        copy.setIsShared(false);
        copy.setIsDefault(false);
        return toVO(recordTemplateRepository.save(copy));
    }

    /** 设为默认模板 */
    @Transactional
    public void setDefault(Long id, Long userId) {
        var entity = findById(id);
        recordTemplateRepository.clearDefault(entity.getEntitySlug(), userId);
        entity.setIsDefault(true);
        recordTemplateRepository.save(entity);
    }

    /** 删除模板 */
    @Transactional
    public void delete(Long id) {
        recordTemplateRepository.deleteById(id);
    }

    private RecordTemplate findById(Long id) {
        return recordTemplateRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "模板不存在"));
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
