package com.xuejiai.aaf.module.ai.aigc.voice.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.intelligent.ai.omni.VoiceEnrollmentService;
import com.xuejiai.aaf.framework.intelligent.ai.omni.VoiceEnrollmentService.CreateVoiceRequest;
import com.xuejiai.aaf.framework.intelligent.ai.speech.CosyVoiceEnrollmentService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.ai.aigc.voice.domain.AiClonedVoice;
import com.xuejiai.aaf.module.ai.aigc.voice.repository.AiClonedVoiceRepository;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoicePageDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.AiClonedVoiceVO;

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

    private final AiClonedVoiceRepository voiceRepository;
    private final VoiceEnrollmentService enrollmentService;
    private final CosyVoiceEnrollmentService cosyEnrollmentService;
    private final ConfigCacheManager configCacheManager;
    private final SpeechService speechService;
    private final StorageService storageService;
    @org.springframework.beans.factory.annotation.Autowired private OperatorContext operatorContext;

    @Override
    protected JpaRepository<AiClonedVoice, Long> getRepository() {
        return voiceRepository;
    }

    @Override
    protected JpaSpecificationExecutor<AiClonedVoice> getSpecExecutor() {
        return voiceRepository;
    }

    @Override
    protected AiClonedVoiceVO toVO(AiClonedVoice e) {
        return new AiClonedVoiceVO(
                e.getId(),
                e.getVoice(),
                e.getPreferredName(),
                e.getTargetModel(),
                e.getSourceAssetId(),
                e.getSampleAudioUrl(),
                e.getUserId(),
                e.getCreateTime());
    }

    /** 创建音色：先调百炼平台复刻，成功后持久化到本地。 覆写 toEntity 无法满足需求（需要远程调用），因此直接覆写 create。 */
    @Override
    @Transactional
    public AiClonedVoiceVO create(AiClonedVoiceCreateDTO dto) {
        // 按 ai_model.capabilities 路由：SPEECH_TTS 走 CosyVoice SDK，其余走 Omni REST
        var aiModel = configCacheManager.getAiModelByModelId(dto.targetModel());
        boolean isTts = aiModel != null && aiModel.hasCapability("SPEECH_TTS");

        String voice;
        if (isTts) {
            voice =
                    cosyEnrollmentService.createVoice(
                            dto.targetModel(),
                            dto.preferredName(),
                            dto.audioData(),
                            dto.language());
        } else {
            voice =
                    enrollmentService.createVoice(
                            new CreateVoiceRequest(
                                    dto.targetModel(),
                                    dto.preferredName(),
                                    dto.audioData(),
                                    dto.text(),
                                    dto.language()));
        }

        // 持久化到本地
        var entity = new AiClonedVoice();
        entity.setVoice(voice);
        entity.setPreferredName(dto.preferredName());
        entity.setTargetModel(dto.targetModel());
        entity.setSourceAssetId(dto.sourceAssetId());
        entity.setUserId(operatorContext.currentUserId().orElseThrow());

        // TTS 类型：生成示例音频并上传 OSS
        if (isTts) {
            try {
                byte[] audioBytes = speechService.synthesize(null, "你好，这是我的专属声音。", voice).audio();
                String key =
                        storageService.upload(
                                new java.io.ByteArrayInputStream(audioBytes),
                                "sample_" + dto.preferredName() + ".wav",
                                "audio/wav");
                entity.setSampleAudioUrl(storageService.getUrl(key));
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
        Long currentUserId = operatorContext.currentUserId().orElseThrow();
        return SpecificationBuilder.<AiClonedVoice>builder()
                .eqIfPresent("userId", currentUserId)
                .eqIfPresent("targetModel", dto.getTargetModel())
                .build();
    }
}
