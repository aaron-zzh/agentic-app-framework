package com.xuejiai.aaf.module.ai.aigc.voice.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.intelligent.ai.omni.VoiceEnrollmentService;
import com.xuejiai.aaf.framework.intelligent.ai.omni.VoiceEnrollmentService.CreateVoiceRequest;
import com.xuejiai.aaf.framework.intelligent.ai.speech.CosyVoiceEnrollmentService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
import com.xuejiai.aaf.module.ai.aigc.voice.domain.AiClonedVoice;
import com.xuejiai.aaf.module.ai.aigc.voice.repository.AiClonedVoiceRepository;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoicePageDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceVO;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 声音复刻 CRUD 服务。
 *
 * <p>创建时调用 {@link VoiceEnrollmentService} 与百炼平台交互， 将返回的 voice 名称持久化到本地，方便后续对话直接查询使用。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiClonedVoiceService
        extends BaseCrudService<
                AiClonedVoice,
                AiClonedVoiceVO,
                AiClonedVoiceCreateDTO,
                AiClonedVoiceUpdateDTO,
                AiClonedVoicePageDTO> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "voice", "preferredName", "targetModel", "createTime");

    private final AiClonedVoiceRepository voiceRepository;
    private final VoiceEnrollmentService enrollmentService;
    private final CosyVoiceEnrollmentService cosyEnrollmentService;
    private final ConfigCacheManager configCacheManager;
    private final SpeechService speechService;
    private final FileStoragePort fileStoragePort;
    private final AigcMediaApi mediaApi;
    @org.springframework.beans.factory.annotation.Autowired private OperatorContext operatorContext;

    @Override
    protected java.util.List<String> optionSearchFields() {
        return java.util.List.of("preferredName", "voice");
    }

    @Override
    protected AiClonedVoiceRepository getRepository() {
        return voiceRepository;
    }

    @Override
    protected AiClonedVoiceVO toVO(AiClonedVoice e) {
        return new AiClonedVoiceVO(
                e.getId(),
                e.getVoice(),
                e.getPreferredName(),
                e.getTargetModel(),
                e.getSourceMediaVersionId(),
                e.getSampleAudioMediaVersionId(),
                e.getUserId(),
                e.getCreateTime());
    }

    /** 创建音色：先调百炼平台复刻，成功后持久化到本地。 覆写 toEntity 无法满足需求（需要远程调用），因此直接覆写 create。 */
    @Override
    @Transactional
    public AiClonedVoiceVO create(AiClonedVoiceCreateDTO dto) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        var sourceMedia =
                requireMediaVersion(
                        dto.sourceMediaVersionId(), userId, AigcMediaType.AUDIO, "复刻原始音频");
        var sourceAudioUrl = sourceMedia.currentVersion().url();

        // 按 ai_model.capabilities 路由：SPEECH_TTS 走 CosyVoice SDK，其余走 Omni REST
        var aiModel = configCacheManager.getAiModelByModelId(dto.targetModel());
        boolean isTts = aiModel != null && aiModel.hasCapability("SPEECH_TTS");

        String voice;
        if (isTts) {
            voice =
                    cosyEnrollmentService.createVoice(
                            dto.targetModel(), dto.preferredName(), sourceAudioUrl, dto.language());
        } else {
            voice =
                    enrollmentService.createVoice(
                            new CreateVoiceRequest(
                                    dto.targetModel(),
                                    dto.preferredName(),
                                    sourceAudioUrl,
                                    dto.text(),
                                    dto.language()));
        }

        var entity = new AiClonedVoice();
        entity.setVoice(voice);
        entity.setPreferredName(dto.preferredName());
        entity.setTargetModel(dto.targetModel());
        entity.setSourceMediaVersionId(dto.sourceMediaVersionId());
        entity.setUserId(userId);

        if (isTts) {
            try {
                var audioBytes = speechService.synthesize(aiModel, "你好，这是我的专属声音。", voice).audio();
                var storedFile =
                        fileStoragePort.uploadFromBytes(
                                audioBytes,
                                "aigc/voice/sample/%s.wav".formatted(UUID.randomUUID()),
                                "audio/wav",
                                userId);
                var sampleMedia =
                        mediaApi.createFromGeneratedFile(
                                new AigcGeneratedMediaCommand(
                                        userId,
                                        "音色示例-" + dto.preferredName(),
                                        AigcMediaType.AUDIO,
                                        storedFile,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null));
                entity.setSampleAudioMediaVersionId(sampleMedia.currentVersion().id());
            } catch (Exception e) {
                log.warn("[ClonedVoice] 示例音频生成失败，不影响音色创建: voice={}", voice, e);
            }
        }

        var saved = voiceRepository.save(entity);
        return toVO(saved);
    }

    @Override
    protected AiClonedVoice toEntity(AiClonedVoiceCreateDTO dto) {
        // create() 已覆写，此方法不会被调用，保留以满足抽象类约束
        throw new UnsupportedOperationException("使用 create() 代替");
    }

    @Override
    protected void updateEntity(AiClonedVoice entity, AiClonedVoiceUpdateDTO dto) {
        if (dto.preferredName() != null) {
            entity.setPreferredName(dto.preferredName());
        }
        if (dto.remark() != null) {
            entity.setRemark(dto.remark());
        }
    }

    @Override
    protected org.springframework.data.jpa.domain.Specification<AiClonedVoice> buildSpec(
            AiClonedVoicePageDTO dto) {
        // BE-8 数据隔离：强制按当前 userId 过滤，忽略请求参数中的 userId
        Long currentUserId = operatorContext.currentOwnerId().orElseThrow();
        return SpecificationBuilder.<AiClonedVoice>builder()
                .eqIfPresent("userId", currentUserId)
                .eqIfPresent("targetModel", dto.getTargetModel())
                .build();
    }

    private AigcMediaView requireMediaVersion(
            Long mediaVersionId, Long userId, AigcMediaType expectedType, String label) {
        var media = mediaApi.getByVersionId(mediaVersionId, userId);
        if (media.mediaType() != expectedType) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "%s媒体类型必须为%s".formatted(label, expectedType));
        }
        return media;
    }
}
