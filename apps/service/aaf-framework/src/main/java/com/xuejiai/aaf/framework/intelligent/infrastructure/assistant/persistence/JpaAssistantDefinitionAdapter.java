package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Actor;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.Lifecycle;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.RiskPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition.TemplateOwnership;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.MemoryStrategy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ActionEffect;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ToolPolicy.ToolRule;
import com.xuejiai.aaf.framework.intelligent.assistant.persona.PersonaRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRole;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 从 ai_assistant 当前行及 ai_assistant_role M2M 装配 Assistant 运行时定义。 */
public final class JpaAssistantDefinitionAdapter implements AssistantDefinitionPort {

    private static final String ACTIVE = "active";
    private static final String DEFAULT_ASSISTANT_CODE = "system.assistant.default-user";

    private final AssistantRepository assistants;
    private final PersonaRepository personas;
    private final AiAssistantRoleRepository bindings;
    private final AiRoleRepository roles;
    private final AiModelRepository models;

    public JpaAssistantDefinitionAdapter(
            AssistantRepository assistants,
            PersonaRepository personas,
            AiAssistantRoleRepository bindings,
            AiRoleRepository roles,
            AiModelRepository models) {
        this.assistants = Objects.requireNonNull(assistants, "assistants 不能为空");
        this.personas = Objects.requireNonNull(personas, "personas 不能为空");
        this.bindings = Objects.requireNonNull(bindings, "bindings 不能为空");
        this.roles = Objects.requireNonNull(roles, "roles 不能为空");
        this.models = Objects.requireNonNull(models, "models 不能为空");
    }

    @Override
    public Optional<AssistantDefinition> findById(TenantId tenantId, AssistantId assistantId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        return assistants.findByCodeAndStatus(assistantId.value(), ACTIVE).map(this::toDomain);
    }

    @Override
    public Optional<AssistantDefinition> findDefaultForUser(TenantId tenantId, UserId userId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(userId, "userId 不能为空");
        var numericUserId = numericId(userId.value());
        var assistant =
                numericUserId == null
                        ? Optional.<AssistantEntity>empty()
                        : assistants.findFirstByUserIdAndStatusOrderByIdAsc(numericUserId, ACTIVE);
        return assistant
                .or(() -> assistants.findByCodeAndStatus(DEFAULT_ASSISTANT_CODE, ACTIVE))
                .map(this::toDomain);
    }

    private AssistantDefinition toDomain(AssistantEntity assistant) {
        var persona =
                personas.findById(assistant.getPersonaId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Assistant Persona 不存在: "
                                                        + assistant.getPersonaId()));
        var roleBindings =
                bindings.findByAssistantIdAndEnabledTrueOrderBySortOrderAsc(assistant.getId());
        if (roleBindings.isEmpty()) {
            throw new IllegalStateException("Assistant 未挂载 Role: " + assistant.getCode());
        }
        var defaultBindings = roleBindings.stream().filter(AiAssistantRole::getIsDefault).toList();
        if (defaultBindings.size() != 1) {
            throw new IllegalStateException(
                    "Assistant 必须且只能有一个 ai_assistant_role.is_default: " + assistant.getCode());
        }
        var roleDefinitions = roleBindings.stream().map(this::role).toList();
        var defaultRoleId = defaultBindings.getFirst().getRoleId();
        var defaultRoleKey =
                roleBindings.stream()
                        .filter(binding -> binding.getRoleId().equals(defaultRoleId))
                        .findFirst()
                        .map(this::role)
                        .map(Role::key)
                        .orElseThrow();
        var ownership =
                assistant.getUserId() == 0
                        ? TemplateOwnership.SYSTEM_MANAGED
                        : TemplateOwnership.USER_OWNED;
        var assistantSkillBindings =
                parseRequiredBindings(assistant.getSkillIds(), "ai_assistant.skill_ids");
        var assistantToolKeys =
                parseRequiredKeys(assistant.getToolWhitelist(), "ai_assistant.tool_whitelist");
        return new AssistantDefinition(
                new AssistantId(assistant.getCode()),
                ownership == TemplateOwnership.SYSTEM_MANAGED ? assistant.getCode() : null,
                null,
                ownership,
                new AssistantVersion(assistant.getVersion()),
                ownership == TemplateOwnership.SYSTEM_MANAGED
                        ? "AAF"
                        : assistant.getUserId().toString(),
                new Actor(
                        "persona:" + persona.getId(),
                        persona.getName(),
                        text(persona.getPersona(), persona.getName()),
                        text(persona.getPersona(), "专业、审慎"),
                        text(persona.getPersona(), "简洁、准确"),
                        text(persona.getSystemPrompt(), "只使用已授权的 Skill 与工具"),
                        persona.getAvatarUrl()),
                roleDefinitions,
                assistantSkillBindings,
                assistantToolKeys,
                defaultRoleKey,
                memoryStrategy(assistant.getMemoryStrategy()),
                modelId(assistant),
                toolPolicy(roleDefinitions, assistantToolKeys),
                Set.of(ControlMode.READ_ONLY, ControlMode.COLLABORATIVE, ControlMode.DELEGATED),
                RiskPolicy.CONFIRM_WRITES,
                ACTIVE.equals(assistant.getStatus()) ? Lifecycle.PUBLISHED : Lifecycle.DISABLED);
    }

    private Role role(AiAssistantRole binding) {
        var entity =
                roles.findById(binding.getRoleId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Assistant Role 不存在: " + binding.getRoleId()));
        if (!ACTIVE.equals(entity.getStatus())) {
            throw new IllegalStateException("Assistant Role 不可执行: " + entity.getCode());
        }
        return new Role(
                entity.getCode(),
                entity.getName(),
                List.of(text(entity.getDescription(), entity.getName())),
                List.of(),
                parseBindings(entity.getSkillIds(), "ai_role.skill_ids"),
                parseKeys(entity.getToolWhitelist(), "toolWhitelist"));
    }

    private static ToolPolicy toolPolicy(List<Role> roles, Set<String> assistantToolKeys) {
        var rules = new LinkedHashMap<String, ToolRule>();
        java.util.stream.Stream.concat(
                        roles.stream().flatMap(role -> role.toolKeys().stream()),
                        assistantToolKeys.stream())
                .distinct()
                .sorted()
                .forEach(
                        tool -> {
                            var effect = effect(tool);
                            rules.put(
                                    tool,
                                    new ToolRule(
                                            tool,
                                            effect,
                                            effect == ActionEffect.REVERSIBLE_WRITE,
                                            "script.execute.javascript".equals(tool)
                                                    || effect == ActionEffect.REVERSIBLE_WRITE
                                                    || effect == ActionEffect.IRREVERSIBLE_WRITE));
                        });
        return new ToolPolicy(rules);
    }

    private static ActionEffect effect(String tool) {
        var normalized = tool.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("handoff")) return ActionEffect.HUMAN_HANDOFF;
        if (normalized.contains("delete") || normalized.contains("publish"))
            return ActionEffect.IRREVERSIBLE_WRITE;
        if (normalized.contains("create")
                || normalized.contains("update")
                || normalized.contains("upsert")
                || normalized.contains("write")) return ActionEffect.REVERSIBLE_WRITE;
        if (normalized.contains("generate")) return ActionEffect.GENERATED_CONTENT;
        return ActionEffect.READ;
    }

    private String modelId(AssistantEntity assistant) {
        if (assistant.getModelId() == null) return null;
        var model =
                models.findById(assistant.getModelId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Assistant 模型不存在: " + assistant.getModelId()));
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new IllegalStateException("Assistant 模型不可用: " + model.getModelId());
        }
        return model.getModelId();
    }

    private static MemoryStrategy memoryStrategy(String value) {
        if (value == null || value.isBlank()) return MemoryStrategy.hybridDefault();
        return switch (MemoryStrategy.Mode.valueOf(value.toUpperCase(java.util.Locale.ROOT))) {
            case KNOWLEDGE_ONLY -> MemoryStrategy.knowledgeOnly();
            case MEMORY_ONLY ->
                    new MemoryStrategy(
                            MemoryStrategy.Mode.MEMORY_ONLY,
                            Set.of("PERSONAL"),
                            Set.of("PERSONAL"),
                            true);
            case HYBRID, PROCEDURAL_FIRST, FULL -> MemoryStrategy.hybridDefault();
        };
    }

    private static List<SkillBinding> parseRequiredBindings(String json, String fieldName) {
        Objects.requireNonNull(json, fieldName + " 不能为空");
        if (json.isBlank()) {
            throw new IllegalStateException(fieldName + " 必须是合法 SkillBinding JSON 数组");
        }
        return parseBindings(json, fieldName);
    }

    private static List<SkillBinding> parseBindings(String json, String fieldName) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 SkillBinding JSON 数组");
            }
            var result = new java.util.ArrayList<SkillBinding>();
            for (var item : root) {
                if (!item.isObject()
                        || item.size() != 2
                        || !item.path("skillKey").isTextual()
                        || !item.path("activationMode").isTextual()) {
                    throw new IllegalStateException(
                            fieldName + " 每项必须且只能包含 skillKey/activationMode");
                }
                result.add(
                        new SkillBinding(
                                item.get("skillKey").textValue(),
                                SkillActivationMode.valueOf(
                                        item.get("activationMode").textValue())));
            }
            return SkillBinding.copyOf(result, fieldName);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(fieldName + " 必须是合法 SkillBinding JSON 数组", failure);
        }
    }

    private static Set<String> parseRequiredKeys(String json, String fieldName) {
        Objects.requireNonNull(json, fieldName + " 不能为空");
        if (json.isBlank()) {
            throw new IllegalStateException(fieldName + " 必须是合法 JSON 字符串数组");
        }
        return parseKeys(json, fieldName);
    }

    private static Set<String> parseKeys(String json, String fieldName) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            var root = JsonUtils.readTreeStrict(json);
            if (!root.isArray()) {
                throw new IllegalStateException(fieldName + " 必须是 JSON 字符串数组");
            }
            var values = new java.util.LinkedHashSet<String>();
            for (var item : root) {
                if (!item.isTextual() || !values.add(item.textValue())) {
                    throw new IllegalStateException(fieldName + " 必须是无重复值的 JSON 字符串数组");
                }
            }
            return java.util.Collections.unmodifiableSet(values);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new IllegalStateException(fieldName + " 必须是合法 JSON 字符串数组", failure);
        }
    }

    private static Long numericId(String value) {
        try {
            var id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String text(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
