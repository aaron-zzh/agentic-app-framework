package com.xuejiai.aaf.module.digitalhuman.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.repository.PresetVoiceRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoiceVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PresetVoiceService {

    private final PresetVoiceRepository presetVoiceRepository;

    /**
     * 获取所有启用的预设音色列表
     */
    public List<PresetVoiceVO> listActive() {
        List<PresetVoice> voices = presetVoiceRepository.findByIsActiveTrue(
                Sort.by(Sort.Direction.ASC, "displayOrder")
        );
        return voices.stream().map(this::toVO).toList();
    }

    /**
     * 获取所有预设音色列表（包括停用的）
     */
    public List<PresetVoiceVO> listAll() {
        List<PresetVoice> voices = presetVoiceRepository.findAll(
                Sort.by(Sort.Direction.ASC, "displayOrder")
        );
        return voices.stream().map(this::toVO).toList();
    }

    private PresetVoiceVO toVO(PresetVoice entity) {
        PresetVoiceVO vo = new PresetVoiceVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setGender(entity.getGender());
        vo.setSampleAudioUrl(entity.getSampleAudioUrl());
        vo.setProvider(entity.getProvider());
        vo.setExternalVoiceId(entity.getExternalVoiceId());
        vo.setDisplayOrder(entity.getDisplayOrder());
        return vo;
    }
}
