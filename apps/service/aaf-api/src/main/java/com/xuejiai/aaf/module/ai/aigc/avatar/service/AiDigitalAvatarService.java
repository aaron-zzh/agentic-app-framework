package com.xuejiai.aaf.module.ai.aigc.avatar.service;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;
import com.xuejiai.aaf.module.ai.aigc.avatar.repository.AiDigitalAvatarRepository;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarPageDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarVO;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;

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

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "detectStatus", "defaultVoice", "createTime");

    private static final String STATUS_PENDING = "PENDING";

    private final AiDigitalAvatarRepository avatarRepository;
    private final AigcMediaApi mediaApi;
    @org.springframework.beans.factory.annotation.Autowired private OperatorContext operatorContext;

    @Override
    protected AiDigitalAvatarRepository getRepository() {
        return avatarRepository;
    }

    @Override
    protected AiDigitalAvatarVO toVO(AiDigitalAvatar e) {
        return new AiDigitalAvatarVO(
                e.getId(),
                e.getName(),
                e.getImageMediaVersionId(),
                e.getSourceMediaVersionId(),
                e.getDetectStatus(),
                e.getDetectReason(),
                e.getDefaultVoice(),
                e.getUserId(),
                e.getCreateTime());
    }

    @Override
    protected AiDigitalAvatar toEntity(AiDigitalAvatarCreateDTO dto) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        requireImageMediaVersion(dto.imageMediaVersionId(), userId, "形象图片");
        if (dto.sourceMediaVersionId() != null) {
            requireImageMediaVersion(dto.sourceMediaVersionId(), userId, "原始形象素材");
        }

        var entity = new AiDigitalAvatar();
        entity.setName(dto.name());
        entity.setImageMediaVersionId(dto.imageMediaVersionId());
        entity.setSourceMediaVersionId(dto.sourceMediaVersionId());
        entity.setDefaultVoice(dto.defaultVoice());
        entity.setDetectStatus(STATUS_PENDING);
        entity.setUserId(userId);
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
        // BE-8 数据隔离：强制按当前 userId 过滤
        Long currentUserId = operatorContext.currentOwnerId().orElseThrow();
        return SpecificationBuilder.<AiDigitalAvatar>builder()
                .eqIfPresent("userId", currentUserId)
                .eqIfPresent("detectStatus", dto.getDetectStatus())
                .build();
    }

    private void requireImageMediaVersion(Long mediaVersionId, Long userId, String label) {
        var media = mediaApi.getByVersionId(mediaVersionId, userId);
        if (media.mediaType() != AigcMediaType.IMAGE) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, label + "媒体类型必须为IMAGE");
        }
    }
}
