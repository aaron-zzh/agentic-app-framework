package com.xuejiai.aaf.module.system.dict.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.dict.domain.DictType;
import com.xuejiai.aaf.module.system.dict.repository.DictDataRepository;
import com.xuejiai.aaf.module.system.dict.repository.DictTypeRepository;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeCreateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeUpdateDTO;
import com.xuejiai.aaf.module.system.dict.vo.DictTypeVO;

import lombok.RequiredArgsConstructor;

/**
 * 字典类型服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictTypeService {

    private final DictTypeRepository dictTypeRepository;
    private final DictDataRepository dictDataRepository;

    public List<DictTypeVO> list() {
        return dictTypeRepository.findAll().stream().map(this::toVO).toList();
    }

    public DictTypeVO getById(Long id) {
        return toVO(requireDictType(id));
    }

    @Transactional
    public DictTypeVO create(DictTypeCreateDTO dto) {
        if (dictTypeRepository.existsByTypeAndDeletedFalse(dto.type())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "字典类型编码已存在");
        }
        if (dictTypeRepository.existsByNameAndDeletedFalse(dto.name())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "字典名称已存在");
        }
        var dictType = new DictType();
        dictType.setName(dto.name());
        dictType.setType(dto.type());
        dictType.setRemark(dto.remark());
        return toVO(dictTypeRepository.save(dictType));
    }

    @Transactional
    public DictTypeVO update(Long id, DictTypeUpdateDTO dto) {
        var dictType = requireDictType(id);
        if (dto.name() != null) {
            if (dictTypeRepository.existsByNameAndDeletedFalse(dto.name())
                    && !dictType.getName().equals(dto.name())) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "字典名称已存在");
            }
            dictType.setName(dto.name());
        }
        if (dto.status() != null) dictType.setStatus(dto.status());
        if (dto.remark() != null) dictType.setRemark(dto.remark());
        return toVO(dictTypeRepository.save(dictType));
    }

    @Transactional
    public void delete(Long id) {
        var dictType = requireDictType(id);
        if (dictDataRepository.countByDictTypeAndDeletedFalse(dictType.getType()) > 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该字典类型下存在字典数据，请先删除");
        }
        dictTypeRepository.deleteById(id);
    }

    private DictType requireDictType(Long id) {
        return dictTypeRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "字典类型不存在"));
    }

    private DictTypeVO toVO(DictType t) {
        return new DictTypeVO(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getStatus(),
                t.getRemark(),
                t.getCreateTime());
    }
}
