package com.xuejiai.aaf.module.digitalhuman.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.DIGITAL_HUMAN_EDIT_FORBIDDEN;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.DIGITAL_HUMAN_FIELD_COMMAND_ONLY;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.PRESET_VOICE_DISABLED;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.PRESET_VOICE_ID_REQUIRED;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.PRESET_VOICE_NOT_FOUND;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.mapper.DigitalHumanConvert;
import com.xuejiai.aaf.module.digitalhuman.repository.DigitalHumanRepository;
import com.xuejiai.aaf.module.digitalhuman.repository.PresetVoiceRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanAppearance;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanChangeVoiceDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanCreateDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanLinkAssistantDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanModelConfig;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanPageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanUpdateDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVoiceConfig;

import lombok.RequiredArgsConstructor;

/**
 * 数字人管理根服务。
 *
 * <p>标准增删改查走 {@link BaseCrudService} 的统一授权管线；音色切换、助理关联、归档与恢复是具名自定义更新命令。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DigitalHumanService
        extends BaseCrudService<
                DigitalHuman,
                DigitalHumanVO,
                DigitalHumanCreateDTO,
                DigitalHumanUpdateDTO,
                DigitalHumanPageDTO> {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_ARCHIVED = "archived";

    /** 无参数命令的占位载荷，仅用于授权摘要。 */
    private static final Map<String, Object> NO_PAYLOAD = Map.of();

    private final DigitalHumanRepository repository;
    private final PresetVoiceRepository presetVoiceRepository;
    private final OperatorContext operatorContext;

    @Override
    protected DigitalHumanRepository getRepository() {
        return repository;
    }

    @Override
    protected DigitalHumanVO toVO(DigitalHuman entity) {
        var currentOwnerId = operatorContext.currentOwnerId().orElse(null);
        return DigitalHumanConvert.INSTANCE
                .toVO(entity)
                .withOwnedByCurrentUser(Objects.equals(entity.getOwnerId(), currentOwnerId));
    }

    @Override
    protected DigitalHuman toEntity(DigitalHumanCreateDTO dto) {
        var entity = DigitalHumanConvert.INSTANCE.toEntity(dto);
        entity.setVoiceConfig(resolveVoiceConfig(dto.voiceConfig()));
        return entity;
    }

    @Override
    protected void beforeUpdate(DigitalHuman entity, DigitalHumanUpdateDTO dto) {
        requireOwner(entity);
        if (!dto.assistantId().isAbsent()) {
            throw exception(DIGITAL_HUMAN_FIELD_COMMAND_ONLY, "assistantId");
        }
        if (!dto.status().isAbsent()) {
            throw exception(DIGITAL_HUMAN_FIELD_COMMAND_ONLY, "status");
        }
    }

    @Override
    protected void beforeDelete(DigitalHuman entity) {
        requireOwner(entity);
    }

    @Override
    protected void updateEntity(DigitalHuman entity, DigitalHumanUpdateDTO dto) {
        entity.setVersion(
                DigitalHumanPatchSupport.requireVersion(
                        entity.getVersion(), dto.expectedVersion()));
        DigitalHumanPatchSupport.required(dto.name(), "name", entity::setName);
        DigitalHumanPatchSupport.nullable(dto.description(), entity::setDescription);
        DigitalHumanPatchSupport.required(
                dto.avatarImageUrl(), "avatarImageUrl", entity::setAvatarImageUrl);
        DigitalHumanPatchSupport.nullable(dto.tags(), entity::setTags);
        DigitalHumanPatchSupport.nullable(
                dto.appearance(), value -> entity.setAppearance(toEntityOrNull(value)));
        DigitalHumanPatchSupport.required(
                dto.voiceConfig(),
                "voiceConfig",
                value -> entity.setVoiceConfig(resolveVoiceConfig(value)));
        DigitalHumanPatchSupport.nullable(
                dto.modelConfig(), value -> entity.setModelConfig(toEntityOrNull(value)));
        DigitalHumanPatchSupport.required(
                dto.syncModelConfig(), "syncModelConfig", entity::setSyncModelConfig);
    }

    @Override
    protected Specification<DigitalHuman> buildSpec(DigitalHumanPageDTO request) {
        return SpecificationBuilder.<DigitalHuman>builder()
                .likeIfPresent("name", request.getName())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    /** 切换音色。 */
    @Transactional
    public DigitalHumanVO changeVoice(Long id, DigitalHumanChangeVoiceDTO command) {
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<
                        DigitalHuman, DigitalHumanChangeVoiceDTO, Object, DigitalHumanVO>(
                        "CHANGE_VOICE",
                        Set.of("voiceConfig"),
                        (entity, request) -> requireOwner(entity),
                        (entity, request) -> {
                            entity.setVoiceConfig(
                                    resolveVoiceConfig(
                                            new DigitalHumanVoiceConfig(
                                                    DigitalHumanVoiceConfig.DEFAULT_PROVIDER,
                                                    request.presetVoiceId(),
                                                    null,
                                                    request.speechRate(),
                                                    request.pitchRate(),
                                                    request.volume())));
                            bumpVersion(entity);
                        },
                        (entity, request) -> null,
                        true,
                        (entity, request, result) -> {},
                        (entity, request, result) -> toVO(entity)));
    }

    /** 关联助理。 */
    @Transactional
    public DigitalHumanVO linkAssistant(Long id, DigitalHumanLinkAssistantDTO command) {
        // TODO: 验证 Assistant 是否存在且属于当前用户
        // TODO: 更新 Assistant 的 digitalHumanId 字段（双向关联）
        return executeCustomUpdateCommand(
                id,
                command,
                new CustomUpdatePlan<
                        DigitalHuman, DigitalHumanLinkAssistantDTO, Object, DigitalHumanVO>(
                        "LINK_ASSISTANT",
                        Set.of("assistantId", "syncModelConfig"),
                        (entity, request) -> requireOwner(entity),
                        (entity, request) -> {
                            entity.setAssistantId(request.assistantId());
                            entity.setSyncModelConfig(
                                    Boolean.TRUE.equals(request.syncModelConfig()));
                            bumpVersion(entity);
                        },
                        (entity, request) -> null,
                        true,
                        (entity, request, result) -> {},
                        (entity, request, result) -> toVO(entity)));
    }

    /** 取消关联助理。 */
    @Transactional
    public DigitalHumanVO unlinkAssistant(Long id) {
        // TODO: 清除 Assistant 的 digitalHumanId 字段
        return executeCustomUpdateCommand(
                id,
                NO_PAYLOAD,
                new CustomUpdatePlan<DigitalHuman, Map<String, Object>, Object, DigitalHumanVO>(
                        "UNLINK_ASSISTANT",
                        Set.of("assistantId", "syncModelConfig"),
                        (entity, request) -> requireOwner(entity),
                        (entity, request) -> {
                            entity.setAssistantId(null);
                            entity.setSyncModelConfig(false);
                            bumpVersion(entity);
                        },
                        (entity, request) -> null,
                        true,
                        (entity, request, result) -> {},
                        (entity, request, result) -> toVO(entity)));
    }

    /** 归档数字人。 */
    @Transactional
    public DigitalHumanVO archive(Long id) {
        return changeStatus(id, "ARCHIVE", STATUS_ARCHIVED);
    }

    /** 恢复数字人。 */
    @Transactional
    public DigitalHumanVO restore(Long id) {
        return changeStatus(id, "RESTORE", STATUS_ACTIVE);
    }

    private DigitalHumanVO changeStatus(Long id, String commandType, String targetStatus) {
        return executeCustomUpdateCommand(
                id,
                NO_PAYLOAD,
                new CustomUpdatePlan<DigitalHuman, Map<String, Object>, Object, DigitalHumanVO>(
                        commandType,
                        Set.of("status"),
                        (entity, request) -> requireOwner(entity),
                        (entity, request) -> {
                            entity.setStatus(targetStatus);
                            bumpVersion(entity);
                        },
                        (entity, request) -> null,
                        true,
                        (entity, request, result) -> {},
                        (entity, request, result) -> toVO(entity)));
    }

    /** 校验预设音色可用，并补全音色名称，返回可持久化的实体配置。 */
    private DigitalHuman.VoiceConfig resolveVoiceConfig(DigitalHumanVoiceConfig source) {
        var voice = requireActiveVoice(source.presetVoiceId());
        var config = DigitalHumanConvert.INSTANCE.toEntity(source);
        if (config.getVoiceName() == null) {
            config.setVoiceName(voice.getName());
        }
        return config;
    }

    private PresetVoice requireActiveVoice(Long presetVoiceId) {
        if (presetVoiceId == null) {
            throw exception(PRESET_VOICE_ID_REQUIRED);
        }
        var voice =
                presetVoiceRepository
                        .findById(presetVoiceId)
                        .orElseThrow(() -> exception(PRESET_VOICE_NOT_FOUND));
        if (!Boolean.TRUE.equals(voice.getIsActive())) {
            throw exception(PRESET_VOICE_DISABLED);
        }
        return voice;
    }

    /** 组织内成员可查看，仅创建者可修改、删除或执行命令。 */
    private void requireOwner(DigitalHuman entity) {
        var currentOwnerId = operatorContext.currentOwnerId().orElse(null);
        if (currentOwnerId == null || !Objects.equals(entity.getOwnerId(), currentOwnerId)) {
            throw exception(DIGITAL_HUMAN_EDIT_FORBIDDEN);
        }
    }

    private static void bumpVersion(DigitalHuman entity) {
        entity.setVersion((entity.getVersion() == null ? 0 : entity.getVersion()) + 1);
    }

    private static DigitalHuman.AppearanceConfig toEntityOrNull(DigitalHumanAppearance value) {
        return value == null ? null : DigitalHumanConvert.INSTANCE.toEntity(value);
    }

    private static DigitalHuman.ModelConfig toEntityOrNull(DigitalHumanModelConfig value) {
        return value == null ? null : DigitalHumanConvert.INSTANCE.toEntity(value);
    }
}
