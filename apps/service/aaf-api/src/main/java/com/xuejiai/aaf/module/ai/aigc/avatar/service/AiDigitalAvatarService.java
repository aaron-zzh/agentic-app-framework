package com.xuejiai.aaf.module.ai.aigc.avatar.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;
import com.xuejiai.aaf.module.ai.aigc.avatar.repository.AiDigitalAvatarRepository;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarPageDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarVO;

import lombok.RequiredArgsConstructor;

/** 数字人形象 CRUD 服务（视频生成能力待 HappyHorse 接入后补充）。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiDigitalAvatarService
        extends BaseCrudService<
                AiDigitalAvatar,
                AiDigitalAvatarVO,
                AiDigitalAvatarCreateDTO,
                AiDigitalAvatarUpdateDTO,
                AiDigitalAvatarPageDTO> {

    private static final String STATUS_PENDING = "PENDING";

    private final AiDigitalAvatarRepository avatarRepository;

    @Override
    protected JpaRepository<AiDigitalAvatar, Long> getRepository() {
        return avatarRepository;
    }

    @Override
    protected JpaSpecificationExecutor<AiDigitalAvatar> getSpecExecutor() {
        return avatarRepository;
    }

    @Override
    protected AiDigitalAvatarVO toVO(AiDigitalAvatar e) {
        return new AiDigitalAvatarVO(
                e.getId(),
                e.getName(),
                e.getImageUrl(),
                e.getSourceAssetId(),
                e.getDetectStatus(),
                e.getDetectReason(),
                e.getDefaultVoice(),
                e.getUserId(),
                e.getCreateTime());
    }

    @Override
    protected AiDigitalAvatar toEntity(AiDigitalAvatarCreateDTO dto) {
        var entity = new AiDigitalAvatar();
        entity.setName(dto.name());
        entity.setImageUrl(dto.imageUrl());
        entity.setSourceAssetId(dto.sourceAssetId());
        entity.setDefaultVoice(dto.defaultVoice());
        entity.setDetectStatus(STATUS_PENDING);
        return entity;
    }

    @Override
    protected void updateEntity(AiDigitalAvatar entity, AiDigitalAvatarUpdateDTO dto) {
        if (dto.name() != null) entity.setName(dto.name());
        if (dto.defaultVoice() != null) entity.setDefaultVoice(dto.defaultVoice());
        if (dto.remark() != null) entity.setRemark(dto.remark());
    }

    @Override
    protected Specification<AiDigitalAvatar> buildSpec(AiDigitalAvatarPageDTO dto) {
        return SpecificationBuilder.<AiDigitalAvatar>builder()
                .eqIfPresent("detectStatus", dto.getDetectStatus())
                .build();
    }
}
