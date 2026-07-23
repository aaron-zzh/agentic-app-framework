package com.xuejiai.aaf.module.system.dict.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.DICT_TYPE_CODE_EXISTS;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.DICT_TYPE_HAS_DATA;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.DICT_TYPE_NAME_EXISTS;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.DICT_TYPE_NOT_FOUND;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
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
public class DictTypeService
        extends BaseCrudService<
                DictType, DictTypeVO, DictTypeCreateDTO, DictTypeUpdateDTO, PageParam> {

    private final DictTypeRepository dictTypeRepository;
    private final DictDataRepository dictDataRepository;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "type", "status", "createTime");

    @Override
    protected DictTypeRepository getRepository() {
        return dictTypeRepository;
    }

    @Override
    protected DictTypeVO toVO(DictType t) {
        return new DictTypeVO(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getStatus(),
                t.getRemark(),
                t.getCreateTime());
    }

    @Override
    protected DictType toEntity(DictTypeCreateDTO dto) {
        if (dictTypeRepository.existsByTypeAndDeletedFalse(dto.type())) {
            throw exception(DICT_TYPE_CODE_EXISTS);
        }
        if (dictTypeRepository.existsByNameAndDeletedFalse(dto.name())) {
            throw exception(DICT_TYPE_NAME_EXISTS);
        }
        var dictType = new DictType();
        dictType.setName(dto.name());
        dictType.setType(dto.type());
        dictType.setRemark(dto.remark());
        return dictType;
    }

    @Override
    protected void updateEntity(DictType dictType, DictTypeUpdateDTO dto) {
        if (dto.name() != null) {
            if (dictTypeRepository.existsByNameAndDeletedFalse(dto.name())
                    && !dictType.getName().equals(dto.name())) {
                throw exception(DICT_TYPE_NAME_EXISTS);
            }
            dictType.setName(dto.name());
        }
        if (dto.status() != null) dictType.setStatus(dto.status());
        if (dto.remark() != null) dictType.setRemark(dto.remark());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var dictType =
                dictTypeRepository.findById(id).orElseThrow(() -> exception(DICT_TYPE_NOT_FOUND));
        if (dictDataRepository.countByDictTypeAndDeletedFalse(dictType.getType()) > 0) {
            throw exception(DICT_TYPE_HAS_DATA);
        }
        dictTypeRepository.deleteById(id);
    }
}
