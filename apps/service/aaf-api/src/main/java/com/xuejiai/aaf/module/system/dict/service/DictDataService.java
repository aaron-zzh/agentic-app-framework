package com.xuejiai.aaf.module.system.dict.service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.dict.domain.DictData;
import com.xuejiai.aaf.module.system.dict.repository.DictDataRepository;
import com.xuejiai.aaf.module.system.dict.repository.DictTypeRepository;
import com.xuejiai.aaf.module.system.dict.vo.DictDataCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictDataVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 字典数据服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictDataService {

    private final DictDataRepository dictDataRepository;
    private final DictTypeRepository dictTypeRepository;

    /** 按字典类型查询数据列表（带缓存）。 */
    @Cacheable(cacheNames = "dict", key = "#dictType")
    public List<DictDataVO> listByType(String dictType) {
        return dictDataRepository
                .findByDictTypeAndDeletedFalseOrderBySort(dictType)
                .stream()
                .map(this::toVO)
                .toList();
    }

    public DictDataVO getById(Long id) {
        return toVO(requireDictData(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "dict", key = "#dto.dictType()")
    public DictDataVO create(DictDataCreateDTO dto) {
        requireDictTypeExists(dto.dictType());
        var data = new DictData();
        data.setDictType(dto.dictType());
        data.setLabel(dto.label());
        data.setValue(dto.value());
        data.setSort(dto.sort() != null ? dto.sort() : 0);
        data.setColorType(dto.colorType());
        data.setCssClass(dto.cssClass());
        data.setRemark(dto.remark());
        return toVO(dictDataRepository.save(data));
    }

    @Transactional
    public DictDataVO update(Long id, DictDataUpdateDTO dto) {
        var data = requireDictData(id);
        if (dto.label() != null) data.setLabel(dto.label());
        if (dto.value() != null) data.setValue(dto.value());
        if (dto.sort() != null) data.setSort(dto.sort());
        if (dto.status() != null) data.setStatus(dto.status());
        if (dto.colorType() != null) data.setColorType(dto.colorType());
        if (dto.cssClass() != null) data.setCssClass(dto.cssClass());
        if (dto.remark() != null) data.setRemark(dto.remark());
        evictCache(data.getDictType());
        return toVO(dictDataRepository.save(data));
    }

    @Transactional
    public void delete(Long id) {
        var data = requireDictData(id);
        evictCache(data.getDictType());
        dictDataRepository.deleteById(id);
    }

    private void requireDictTypeExists(String dictType) {
        if (!dictTypeRepository.existsByTypeAndDeletedFalse(dictType)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "字典类型不存在");
        }
    }

    private DictData requireDictData(Long id) {
        return dictDataRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "字典数据不存在"));
    }

    @CacheEvict(cacheNames = "dict", key = "#dictType")
    public void evictCache(String dictType) {}

    private DictDataVO toVO(DictData d) {
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
}
