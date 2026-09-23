package com.xuejiai.aaf.module.digitalhuman.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.dto.ChangeVoiceRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.CreateDigitalHumanRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.LinkAssistantRequest;
import com.xuejiai.aaf.module.digitalhuman.dto.UpdateDigitalHumanRequest;
import com.xuejiai.aaf.module.digitalhuman.repository.DigitalHumanRepository;
import com.xuejiai.aaf.module.digitalhuman.repository.PresetVoiceRepository;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DigitalHumanService {

    private final DigitalHumanRepository digitalHumanRepository;
    private final PresetVoiceRepository presetVoiceRepository;
    private final OperatorContext operatorContext;

    /**
     * 创建数字人（系统内部调用，来自 AIGC Blueprint postDeliveryAction）
     */
    @Transactional
    public DigitalHumanVO create(CreateDigitalHumanRequest request) {
        Long userId = operatorContext.currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        Long orgId = operatorContext.currentOrgId().orElse(null);
        Long workspaceId = operatorContext.currentWorkspaceId().orElse(null);

        // 验证预设音色
        PresetVoice presetVoice = presetVoiceRepository.findById(request.getVoiceConfig().getPresetVoiceId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BAD_REQUEST, "预设音色不存在"));

        if (!presetVoice.getIsActive()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "预设音色已停用");
        }

        // 补充音色名称
        if (request.getVoiceConfig().getVoiceName() == null) {
            request.getVoiceConfig().setVoiceName(presetVoice.getName());
        }

        DigitalHuman entity = new DigitalHuman();
        entity.setUserId(userId);
        entity.setOrgId(orgId);
        entity.setWorkspaceId(workspaceId);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setAvatarImageUrl(request.getAvatarImageUrl());
        entity.setTags(request.getTags());
        entity.setAppearance(request.getAppearance());
        entity.setVoiceConfig(request.getVoiceConfig());
        entity.setModelConfig(request.getModelConfig());
        entity.setStatus("active");
        entity.setGenerationSource(request.getGenerationSource());
        entity.setAigcProjectId(request.getAigcProjectId());
        entity.setVideoCount(0);
        entity.setTotalDuration(0);
        entity.setCreditsConsumed(0);

        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 分页查询数字人列表
     */
    public Page<DigitalHumanVO> list(String name, List<String> tags, String status, Pageable pageable) {
        Long userId = operatorContext.currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        Long orgId = operatorContext.currentOrgId().orElse(null);

        Specification<DigitalHuman> spec = Specification.where(null);

        // 权限过滤：创建者或组织成员
        spec = spec.and((root, query, cb) ->
                cb.or(
                        cb.equal(root.get("userId"), userId),
                        orgId != null ? cb.equal(root.get("orgId"), orgId) : cb.disjunction()
                )
        );

        // 名称模糊查询
        if (name != null && !name.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("name"), "%" + name + "%"));
        }

        // 标签过滤
        if (tags != null && !tags.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("tags").in(tags));
        }

        // 状态过滤
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        return digitalHumanRepository.findAll(spec, pageable).map(this::toVO);
    }

    /**
     * 获取数字人详情
     */
    public DigitalHumanVO getById(Long id) {
        DigitalHuman entity = findByIdAndCheckAccess(id);
        return toVO(entity);
    }

    /**
     * 更新数字人
     */
    @Transactional
    public DigitalHumanVO update(Long id, UpdateDigitalHumanRequest request) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);

        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getAvatarImageUrl() != null) {
            entity.setAvatarImageUrl(request.getAvatarImageUrl());
        }
        if (request.getTags() != null) {
            entity.setTags(request.getTags());
        }
        if (request.getAppearance() != null) {
            entity.setAppearance(request.getAppearance());
        }
        if (request.getVoiceConfig() != null) {
            entity.setVoiceConfig(request.getVoiceConfig());
        }
        if (request.getModelConfig() != null) {
            entity.setModelConfig(request.getModelConfig());
        }
        if (request.getSyncModelConfig() != null) {
            entity.setSyncModelConfig(request.getSyncModelConfig());
        }

        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 删除数字人
     */
    @Transactional
    public void delete(Long id) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);
        digitalHumanRepository.delete(entity);
    }

    /**
     * 切换音色
     */
    @Transactional
    public DigitalHumanVO changeVoice(Long id, ChangeVoiceRequest request) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);

        // 验证预设音色
        PresetVoice presetVoice = presetVoiceRepository.findById(request.getPresetVoiceId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BAD_REQUEST, "预设音色不存在"));

        if (!presetVoice.getIsActive()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "预设音色已停用");
        }

        // 更新音色配置
        DigitalHuman.VoiceConfig voiceConfig = entity.getVoiceConfig();
        if (voiceConfig == null) {
            voiceConfig = new DigitalHuman.VoiceConfig();
        }
        voiceConfig.setProvider("aliyun");
        voiceConfig.setPresetVoiceId(request.getPresetVoiceId());
        voiceConfig.setVoiceName(presetVoice.getName());
        voiceConfig.setSpeechRate(request.getSpeechRate());
        voiceConfig.setPitchRate(request.getPitchRate());
        voiceConfig.setVolume(request.getVolume());

        entity.setVoiceConfig(voiceConfig);
        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 关联助理
     */
    @Transactional
    public DigitalHumanVO linkAssistant(Long id, LinkAssistantRequest request) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);

        // TODO: 验证 Assistant 是否存在且属于当前用户
        // TODO: 更新 Assistant 的 digitalHumanId 字段（双向关联）

        entity.setAssistantId(request.getAssistantId());
        entity.setSyncModelConfig(request.getSyncModelConfig());

        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 取消关联助理
     */
    @Transactional
    public DigitalHumanVO unlinkAssistant(Long id) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);

        // TODO: 清除 Assistant 的 digitalHumanId 字段

        entity.setAssistantId(null);
        entity.setSyncModelConfig(false);

        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 归档数字人
     */
    @Transactional
    public DigitalHumanVO archive(Long id) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);
        entity.setStatus("archived");
        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 恢复数字人
     */
    @Transactional
    public DigitalHumanVO restore(Long id) {
        DigitalHuman entity = findByIdAndCheckEditPermission(id);
        entity.setStatus("active");
        DigitalHuman saved = digitalHumanRepository.save(entity);
        return toVO(saved);
    }

    /**
     * 更新使用统计（视频生成完成后调用）
     */
    @Transactional
    public void updateUsageStats(Long id, Integer videoCount, Integer duration, Integer creditsConsumed) {
        DigitalHuman entity = digitalHumanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "数字人不存在"));

        entity.setVideoCount(entity.getVideoCount() + videoCount);
        entity.setTotalDuration(entity.getTotalDuration() + duration);
        entity.setCreditsConsumed(entity.getCreditsConsumed() + creditsConsumed);
        entity.setLastUsedAt(LocalDateTime.now());

        digitalHumanRepository.save(entity);
    }

    // ==================== 私有方法 ====================

    private DigitalHuman findByIdAndCheckAccess(Long id) {
        DigitalHuman entity = digitalHumanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "数字人不存在"));

        Long userId = operatorContext.currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        Long orgId = operatorContext.currentOrgId().orElse(null);

        // 检查访问权限：创建者或组织成员
        boolean canAccess = entity.getUserId().equals(userId) ||
                (orgId != null && entity.getOrgId() != null && entity.getOrgId().equals(orgId));

        if (!canAccess) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问此数字人");
        }

        return entity;
    }

    private DigitalHuman findByIdAndCheckEditPermission(Long id) {
        DigitalHuman entity = digitalHumanRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "数字人不存在"));

        Long userId = operatorContext.currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        // 仅创建者可编辑
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅创建者可编辑数字人");
        }

        return entity;
    }

    private DigitalHumanVO toVO(DigitalHuman entity) {
        DigitalHumanVO vo = new DigitalHumanVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setAvatarImageUrl(entity.getAvatarImageUrl());
        vo.setTags(entity.getTags());
        vo.setAppearance(convertAppearance(entity.getAppearance()));
        vo.setVoiceConfig(convertVoiceConfig(entity.getVoiceConfig()));
        vo.setModelConfig(convertModelConfig(entity.getModelConfig()));
        vo.setAssistantId(entity.getAssistantId());
        vo.setSyncModelConfig(entity.getSyncModelConfig());
        vo.setVideoCount(entity.getVideoCount());
        vo.setTotalDuration(entity.getTotalDuration());
        vo.setCreditsConsumed(entity.getCreditsConsumed());
        vo.setLastUsedAt(entity.getLastUsedAt());
        vo.setStatus(entity.getStatus());
        vo.setGenerationSource(entity.getGenerationSource());
        vo.setAigcProjectId(entity.getAigcProjectId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private DigitalHumanVO.AppearanceConfig convertAppearance(DigitalHuman.AppearanceConfig source) {
        if (source == null) {
            return null;
        }
        DigitalHumanVO.AppearanceConfig target = new DigitalHumanVO.AppearanceConfig();
        target.setAge(source.getAge());
        target.setGender(source.getGender());
        target.setStyle(source.getStyle());
        target.setPrompt(source.getPrompt());
        return target;
    }

    private DigitalHumanVO.VoiceConfig convertVoiceConfig(DigitalHuman.VoiceConfig source) {
        if (source == null) {
            return null;
        }
        DigitalHumanVO.VoiceConfig target = new DigitalHumanVO.VoiceConfig();
        target.setProvider(source.getProvider());
        target.setPresetVoiceId(source.getPresetVoiceId());
        target.setVoiceName(source.getVoiceName());
        target.setSpeechRate(source.getSpeechRate());
        target.setPitchRate(source.getPitchRate());
        target.setVolume(source.getVolume());
        return target;
    }

    private DigitalHumanVO.ModelConfig convertModelConfig(DigitalHuman.ModelConfig source) {
        if (source == null) {
            return null;
        }
        DigitalHumanVO.ModelConfig target = new DigitalHumanVO.ModelConfig();
        target.setProvider(source.getProvider());
        target.setModel(source.getModel());
        target.setTemperature(source.getTemperature());
        target.setMaxTokens(source.getMaxTokens());
        target.setSystemPrompt(source.getSystemPrompt());
        return target;
    }
}
