package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import static com.xuejiai.aaf.framework.intelligent.assistant.SystemAssistantTemplateIds.DEFAULT_USER;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantProvisioningPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 从数据库系统模板创建用户默认 Assistant 副本。 */
public class JpaAssistantProvisioningAdapter implements AssistantProvisioningPort {

    private static final String ACTIVE = "active";
    private static final String DEFAULT_USER_CODE_PREFIX = "user.assistant.default.";

    private final AssistantRepository assistants;
    private final AiAssistantRoleRepository bindings;

    public JpaAssistantProvisioningAdapter(
            AssistantRepository assistants, AiAssistantRoleRepository bindings) {
        this.assistants = Objects.requireNonNull(assistants, "assistants 不能为空");
        this.bindings = Objects.requireNonNull(bindings, "bindings 不能为空");
    }

    @Override
    @Transactional
    public void provisionDefaultForUser(UserId userId) {
        var numericUserId = requireNumericUserId(userId);
        if (assistants
                .findFirstByUserIdAndIsDefaultTrueAndStatusOrderByIdAsc(numericUserId, ACTIVE)
                .isPresent()) {
            return;
        }
        var template =
                assistants
                        .findByCodeAndStatus(DEFAULT_USER, ACTIVE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "默认用户 Assistant 模板不存在: " + DEFAULT_USER));
        if (!Long.valueOf(0L).equals(template.getUserId())) {
            throw new IllegalStateException("默认用户 Assistant 模板必须由系统管理");
        }
        var copy = new AssistantEntity();
        copy.setCode(DEFAULT_USER_CODE_PREFIX + numericUserId);
        copy.setUserId(numericUserId);
        copy.setOwnerId(numericUserId);
        copy.setSourceSystemKey(DEFAULT_USER);
        copy.setIsDefault(true);
        copy.setPersonaId(template.getPersonaId());
        copy.setModelId(template.getModelId());
        copy.setMemoryStrategy(template.getMemoryStrategy());
        copy.setSkillIds(template.getSkillIds());
        copy.setToolWhitelist(template.getToolWhitelist());
        copy.setStatus(ACTIVE);
        var saved = assistants.save(copy);
        var templateBindings = bindings.findByAssistantIdOrderBySortOrderAsc(template.getId());
        if (templateBindings.isEmpty()) {
            throw new IllegalStateException("默认用户 Assistant 模板未挂载 Role");
        }
        var copiedBindings =
                templateBindings.stream()
                        .map(binding -> copyBinding(binding, saved.getId(), numericUserId))
                        .toList();
        bindings.saveAll(copiedBindings);
    }

    private static AiAssistantRole copyBinding(
            AiAssistantRole source, Long assistantId, Long ownerId) {
        var target = new AiAssistantRole();
        target.setAssistantId(assistantId);
        target.setRoleId(source.getRoleId());
        target.setIsDefault(source.getIsDefault());
        target.setSortOrder(source.getSortOrder());
        target.setEnabled(source.getEnabled());
        target.setOwnerId(ownerId);
        return target;
    }

    private static Long requireNumericUserId(UserId userId) {
        Objects.requireNonNull(userId, "userId 不能为空");
        try {
            var numericUserId = Long.parseLong(userId.value());
            if (numericUserId <= 0) {
                throw new IllegalArgumentException("userId 必须大于 0");
            }
            return numericUserId;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("userId 必须是数字", failure);
        }
    }
}
