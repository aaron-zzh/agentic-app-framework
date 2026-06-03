package com.xuejiai.aaf.module.system.dict.service;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.dict.domain.DictData;
import com.xuejiai.aaf.module.system.dict.repository.DictDataRepository;
import com.xuejiai.aaf.module.system.dict.repository.DictTypeRepository;
import com.xuejiai.aaf.module.system.dict.vo.DictDataCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataVO;

import lombok.RequiredArgsConstructor;

/**
 * 字典数据服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictDataService
        extends BaseCrudService<
                DictData, DictDataVO, DictDataCreateDTO, DictDataUpdateDTO, PageParam> {

    private final DictDataRepository dictDataRepository;
    private final DictTypeRepository dictTypeRepository;

    @Override
    protected JpaRepository<DictData, Long> getRepository() {
        return dictDataRepository;
    }

    @Override
    protected JpaSpecificationExecutor<DictData> getSpecExecutor() {
        return dictDataRepository;
    }

    @Override
    protected DictDataVO toVO(DictData d) {
        return new DictDataVO(
                d.getId(),
                d.getDictType(),
                d.getLabel(),
                d.getValue(),
                d.getSort(),
                d.getStatus(),
                d.getColorType(),
                d.getCssClass(),
                d.getRemark(),
                d.getCreateTime());
    }

    @Override
    protected DictData toEntity(DictDataCreateDTO dto) {
        requireDictTypeExists(dto.dictType());
        var data = new DictData();
        data.setDictType(dto.dictType());
        data.setLabel(dto.label());
        data.setValue(dto.value());
        data.setSort(dto.sort() != null ? dto.sort() : 0);
        data.setColorType(dto.colorType());
        data.setCssClass(dto.cssClass());
        data.setRemark(dto.remark());
        return data;
    }

    @Override
    protected void updateEntity(DictData data, DictDataUpdateDTO dto) {
        if (dto.label() != null) data.setLabel(dto.label());
        if (dto.value() != null) data.setValue(dto.value());
        if (dto.sort() != null) data.setSort(dto.sort());
        if (dto.status() != null) data.setStatus(dto.status());
        if (dto.colorType() != null) data.setColorType(dto.colorType());
        if (dto.cssClass() != null) data.setCssClass(dto.cssClass());
        if (dto.remark() != null) data.setRemark(dto.remark());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "dict", allEntries = true)
    public DictDataVO create(DictDataCreateDTO dto) {
        return super.create(dto);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "dict", allEntries = true)
    public DictDataVO update(Long id, DictDataUpdateDTO dto) {
        return super.update(id, dto);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "dict", allEntries = true)
    public void delete(Long id) {
        super.delete(id);
    }

    @Override
    protected String entityName() {
        return "字典数据";
    }

    // ─── 自定义查询 ───

    @Cacheable(cacheNames = "dict", key = "'__all__'")
    public List<DictDataVO> listAll() {
        return dictDataRepository.findAllEnabledOrderByDictTypeAndSort().stream()
                .map(this::toVO)
                .toList();
    }

    @Cacheable(cacheNames = "dict", key = "#dictType")
    public List<DictDataVO> listByType(String dictType) {
        return dictDataRepository
                .findByDictTypeAndStatusAndDeletedFalseOrderBySort(dictType, 0)
                .stream()
                .map(this::toVO)
                .toList();
    }

    public String getLabelByValue(String dictType, String value) {
        return dictDataRepository
                .findByDictTypeAndValueAndDeletedFalse(dictType, value)
                .map(DictData::getLabel)
                .orElse(value);
    }

    public Optional<String> getValueByLabel(String dictType, String label) {
        return dictDataRepository
                .findByDictTypeAndLabelAndDeletedFalse(dictType, label)
                .map(DictData::getValue);
    }

    private void requireDictTypeExists(String dictType) {
        if (!dictTypeRepository.existsByTypeAndDeletedFalse(dictType)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "字典类型不存在");
        }
    }
}
