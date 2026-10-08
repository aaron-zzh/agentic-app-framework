package com.xuejiai.aaf.module.digitalhuman.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.mapper.PresetVoiceConvert;
import com.xuejiai.aaf.module.digitalhuman.repository.PresetVoiceRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoicePageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoiceVO;

import lombok.RequiredArgsConstructor;

/** 数字人预设音色只读服务；音色库由数据库种子维护。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresetVoiceService
        extends ReadonlyCrudService<PresetVoice, PresetVoiceVO, PresetVoicePageDTO> {

    private final PresetVoiceRepository repository;

    @Override
    protected PresetVoiceRepository getRepository() {
        return repository;
    }

    @Override
    protected PresetVoiceVO toVO(PresetVoice entity) {
        return PresetVoiceConvert.INSTANCE.toVO(entity);
    }

    @Override
    protected Specification<PresetVoice> buildSpec(PresetVoicePageDTO request) {
        return SpecificationBuilder.<PresetVoice>builder()
                .eqIfPresent("isActive", request.getIsActive())
                .eqIfPresent("provider", request.getProvider())
                .eqIfPresent("gender", request.getGender())
                .build();
    }
}
