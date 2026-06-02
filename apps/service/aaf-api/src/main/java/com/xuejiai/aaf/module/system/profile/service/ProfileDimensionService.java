package com.xuejiai.aaf.module.system.profile.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.profile.domain.ProfileDimension;
import com.xuejiai.aaf.module.system.profile.repository.ProfileDimensionRepository;

import lombok.RequiredArgsConstructor;

/** 画像维度定义管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileDimensionService {

    private final ProfileDimensionRepository dimensionRepository;

    /** 获取所有启用的维度（按排序） */
    public List<ProfileDimension> listEnabled() {
        return dimensionRepository.findByStatusAndDeletedFalseOrderBySortOrder(0);
    }

    /** 按分组获取维度 */
    public List<ProfileDimension> listByGroup(String groupCode) {
        return dimensionRepository.findByGroupCodeAndStatusAndDeletedFalseOrderBySortOrder(
                groupCode, 0);
    }

    /** 获取 AI 可见的维度 */
    public List<ProfileDimension> listAiVisible() {
        return dimensionRepository.findByAiVisibleTrueAndStatusAndDeletedFalse(0);
    }

    /** 创建维度 */
    @Transactional
    public ProfileDimension create(ProfileDimension dimension) {
        dimensionRepository
                .findByCodeAndDeletedFalse(dimension.getCode())
                .ifPresent(
                        d -> {
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "维度编码已存在: " + d.getCode());
                        });
        return dimensionRepository.save(dimension);
    }

    /** 更新维度 */
    @Transactional
    public ProfileDimension update(Long id, ProfileDimension updated) {
        var dim =
                dimensionRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "维度不存在"));
        dim.setName(updated.getName());
        dim.setGroupCode(updated.getGroupCode());
        dim.setValueType(updated.getValueType());
        dim.setEnumOptions(updated.getEnumOptions());
        dim.setUnit(updated.getUnit());
        dim.setSource(updated.getSource());
        dim.setSortOrder(updated.getSortOrder());
        dim.setRequired(updated.getRequired());
        dim.setSearchable(updated.getSearchable());
        dim.setAiVisible(updated.getAiVisible());
        return dimensionRepository.save(dim);
    }
}
