package com.xuejiai.aaf.module.ai.aigc.avatar.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.ai.avatar.AvatarVideoService;
import com.xuejiai.aaf.framework.intelligent.ai.avatar.AvatarVideoService.SubmitRequest;
import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;
import com.xuejiai.aaf.module.ai.aigc.avatar.repository.AiDigitalAvatarRepository;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarPageDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarVO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AvatarVideoGenerateDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数字人形象 CRUD 服务。
 *
 * <p>创建时自动调用 wan2.2-s2v-detect 检测图片合规性， 检测结果写回 detect_status / detect_reason。 检测通过后可调 {@link
 * #generateVideo} 提交视频生成任务（走 aigc_task，type=AVATAR_VIDEO）。
 *
 * @author AaronZZH
 */
@Slf4j
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

    private static final String AVATAR_VIDEO_TYPE = "AVATAR_VIDEO";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PASSED = "PASSED";
    private static final String STATUS_FAILED = "FAILED";

    private final AiDigitalAvatarRepository avatarRepository;
    private final AigcTaskRepository aigcTaskRepository;
    private final AvatarVideoService avatarVideoService;

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

    /** 创建形象并立即检测图片合规性。 检测结果同步写回 detect_status，调用方可根据返回 VO 的状态决定是否提交视频生成。 */
    @Override
    @Transactional
    public AiDigitalAvatarVO create(AiDigitalAvatarCreateDTO dto) {
        var entity = toEntity(dto);
        var saved = avatarRepository.save(entity);

        // 同步检测图片
        try {
            var result = avatarVideoService.detect(dto.imageUrl());
            saved.setDetectStatus(result.passed() ? STATUS_PASSED : STATUS_FAILED);
            saved.setDetectReason(result.reason());
            avatarRepository.save(saved);
            log.info("[Avatar] 图片检测完成: id={}, passed={}", saved.getId(), result.passed());
        } catch (Exception e) {
            log.error("[Avatar] 图片检测异常: id={}", saved.getId(), e);
            saved.setDetectStatus(STATUS_FAILED);
            saved.setDetectReason("检测服务异常: " + e.getMessage());
            avatarRepository.save(saved);
        }

        return toVO(saved);
    }

    /** 重新检测图片合规性（适用于检测失败后用户替换图片 URL 的场景）。 */
    @Transactional
    public AvatarVideoService.DetectResult reDetect(Long avatarId) {
        var avatar =
                avatarRepository
                        .findById(avatarId)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var result = avatarVideoService.detect(avatar.getImageUrl());
        avatar.setDetectStatus(result.passed() ? STATUS_PASSED : STATUS_FAILED);
        avatar.setDetectReason(result.reason());
        avatarRepository.save(avatar);
        return result;
    }

    /**
     * 提交数字人视频生成任务。 要求形象检测状态为 PASSED，任务记录写入 aigc_task（type=AVATAR_VIDEO）。
     *
     * @param avatarId 数字人形象 ID
     * @param dto 视频生成参数
     * @param userId 发起用户 ID
     * @return aigc_task.id
     */
    @Transactional
    public Long generateVideo(Long avatarId, AvatarVideoGenerateDTO dto, Long userId) {
        var avatar =
                avatarRepository
                        .findById(avatarId)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));

        if (!STATUS_PASSED.equals(avatar.getDetectStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "形象图片未通过检测，无法生成视频");
        }

        // 音色：优先用请求中未传，则取形象绑定的默认音色
        var audioUrl = dto.audioUrl();

        // 调框架层提交异步任务
        var thirdTaskId =
                avatarVideoService.submit(
                        new SubmitRequest(
                                avatar.getImageUrl(), audioUrl, dto.style(), dto.resolution()));

        // 写入 aigc_task 统一管理
        var task = new AigcTask();
        task.setUserId(userId);
        task.setType(AVATAR_VIDEO_TYPE);
        task.setStatus(STATUS_PENDING);
        task.setModel("wan2.2-s2v");
        task.setTaskId(thirdTaskId);
        task.setParams("{\"avatarId\":" + avatarId + "}");
        aigcTaskRepository.save(task);

        log.info(
                "[Avatar] 视频生成任务已提交: avatarId={}, thirdTaskId={}, aigcTaskId={}",
                avatarId,
                thirdTaskId,
                task.getId());
        return task.getId();
    }
}
