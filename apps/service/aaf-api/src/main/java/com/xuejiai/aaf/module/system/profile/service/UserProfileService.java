package com.xuejiai.aaf.module.system.profile.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.profile.domain.ProfileDimensionValue;
import com.xuejiai.aaf.module.system.profile.domain.UserProfile;
import com.xuejiai.aaf.module.system.profile.repository.ProfileDimensionValueRepository;
import com.xuejiai.aaf.module.system.profile.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 用户画像服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final ProfileDimensionValueRepository valueRepository;
    private final ProfileDimensionService dimensionService;

    /** 获取或创建用户画像 */
    @Transactional
    public UserProfile getOrCreate(Long userId) {
        return profileRepository.findByUserIdAndDeletedFalse(userId).orElseGet(() -> {
            var profile = new UserProfile();
            profile.setUserId(userId);
            return profileRepository.save(profile);
        });
    }

    /** 获取用户所有维度值 */
    public List<ProfileDimensionValue> getDimensionValues(Long userId) {
        return valueRepository.findByUserIdAndDeletedFalse(userId);
    }

    /** 设置维度值 */
    @Transactional
    public ProfileDimensionValue setValue(
            Long userId, Long dimensionId, String valueText,
            BigDecimal valueNumber, String valueTags, String source) {
        var value = valueRepository
                .findByUserIdAndDimensionIdAndDeletedFalse(userId, dimensionId)
                .orElseGet(() -> {
                    var v = new ProfileDimensionValue();
                    v.setUserId(userId);
                    v.setDimensionId(dimensionId);
                    return v;
                });
        value.setValueText(valueText);
        value.setValueNumber(valueNumber);
        value.setValueTags(valueTags);
        value.setSource(source);
        value.setConfidence(
                "manual".equals(source) ? BigDecimal.ONE : new BigDecimal("0.80"));
        return valueRepository.save(value);
    }

    /**
     * 构建 AI 上下文摘要——拼接 ai_visible=true 的维度值。
     * 供 AssistantMessageHandler 注入 system prompt。
     */
    public String buildAiContext(Long userId) {
        var aiDimensions = dimensionService.listAiVisible();
        if (aiDimensions.isEmpty()) {
            return "";
        }
        var dimIds = aiDimensions.stream().map(d -> d.getId()).toList();
        var values = valueRepository.findByUserIdAndDimensionIdInAndDeletedFalse(userId, dimIds);
        if (values.isEmpty()) {
            return "";
        }

        // 构建 dimensionId → name 映射
        var dimNameMap = aiDimensions.stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getName()));

        var sb = new StringBuilder();
        for (var v : values) {
            var name = dimNameMap.get(v.getDimensionId());
            var display = v.getValueText() != null ? v.getValueText()
                    : v.getValueNumber() != null ? v.getValueNumber().toPlainString()
                    : v.getValueTags();
            if (name != null && display != null) {
                if (!sb.isEmpty()) sb.append("，");
                sb.append(name).append("：").append(display);
            }
        }
        return sb.toString();
    }
}
