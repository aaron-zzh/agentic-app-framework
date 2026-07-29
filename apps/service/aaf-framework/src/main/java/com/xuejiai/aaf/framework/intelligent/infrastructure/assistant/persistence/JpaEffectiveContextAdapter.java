package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantTask;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceType;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillRoute;
import com.xuejiai.aaf.framework.intelligent.assistant.port.EffectiveContextPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** 用户可管理来源的 PostgreSQL 有效上下文实现。 */
public final class JpaEffectiveContextAdapter implements EffectiveContextPort {

    private final ContextSourcePreferenceRepository preferences;
    private final EffectiveContextManifestRepository manifests;

    public JpaEffectiveContextAdapter(
            ContextSourcePreferenceRepository preferences,
            EffectiveContextManifestRepository manifests) {
        this.preferences = Objects.requireNonNull(preferences, "preferences 不能为空");
        this.manifests = Objects.requireNonNull(manifests, "manifests 不能为空");
    }

    @Override
    @Transactional
    public EffectiveContextManifest resolve(
            TenantId tenantId,
            UserId userId,
            AssistantDefinition definition,
            AssistantTask task,
            SkillRoute route,
            List<SourceReference> candidates,
            Instant at) {
        var configured = listPreferences(tenantId, userId, definition.assistantId());
        var dispositions = new LinkedHashMap<String, Disposition>();
        configured.forEach(
                p -> dispositions.put(key(p.sourceType(), p.sourceKey()), p.disposition()));
        var unique = new LinkedHashMap<String, SourceReference>();
        var subagentSpec = route.subagentSpec();
        var sourceVersion =
                switch (subagentSpec) {
                    case SubagentSpec.Predefined predefined -> Long.toString(predefined.version());
                    case SubagentSpec.Dynamic ignored -> "dynamic";
                };
        var displayName =
                switch (subagentSpec) {
                    case SubagentSpec.Predefined predefined -> predefined.identifier();
                    case SubagentSpec.Dynamic dynamic -> "动态子智能体：" + dynamic.identifier();
                };
        add(
                unique,
                new SourceReference(
                        SourceType.RULE,
                        definition.role().key(),
                        definition.version().toString(),
                        "ASSISTANT",
                        "当前 Assistant 角色与职责边界",
                        definition.role().name(),
                        false));
        add(
                unique,
                new SourceReference(
                        SourceType.SKILL,
                        route.skillKey(),
                        sourceVersion,
                        "TASK",
                        "用户意图命中该技能路由",
                        displayName,
                        false));
        candidates.stream()
                .filter(
                        source -> {
                            var disposition =
                                    dispositions.getOrDefault(
                                            key(source.type(), source.sourceKey()),
                                            Disposition.DEFAULT);
                            return !source.userManageable()
                                    || (disposition != Disposition.DISABLED
                                            && disposition != Disposition.REMOVED);
                        })
                .sorted(
                        Comparator.comparingInt(
                                source ->
                                        dispositions.getOrDefault(
                                                                key(
                                                                        source.type(),
                                                                        source.sourceKey()),
                                                                Disposition.DEFAULT)
                                                        == Disposition.PREFERRED
                                                ? 0
                                                : 1))
                .forEach(source -> add(unique, source));
        var manifest =
                new EffectiveContextManifest(
                        task.taskId(),
                        definition.assistantId(),
                        definition.version(),
                        route.skillKey(),
                        new ArrayList<>(unique.values()),
                        at);
        var entity =
                manifests
                        .findByTenantIdAndTaskId(tenantId.value(), task.taskId().value())
                        .orElseGet(EffectiveContextManifestEntity::new);
        entity.setTenantId(tenantId.value());
        entity.setTaskId(task.taskId().value());
        entity.setManifest(manifest);
        entity.setCreatedAt(at);
        manifests.save(entity);
        return manifest;
    }

    @Override
    @Transactional
    public SourcePreference configure(SourcePreference preference) {
        var entity =
                preferences
                        .findByTenantIdAndUserIdAndAssistantIdAndSourceTypeAndSourceKey(
                                preference.tenantId().value(),
                                preference.userId().value(),
                                preference.assistantId().value(),
                                preference.sourceType().name(),
                                preference.sourceKey())
                        .orElseGet(ContextSourcePreferenceEntity::new);
        entity.setTenantId(preference.tenantId().value());
        entity.setUserId(preference.userId().value());
        entity.setAssistantId(preference.assistantId().value());
        entity.setSourceType(preference.sourceType().name());
        entity.setSourceKey(preference.sourceKey());
        entity.setDisposition(preference.disposition().name());
        entity.setReason(preference.reason());
        entity.setUpdatedAt(preference.updatedAt());
        return toDomain(preferences.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SourcePreference> listPreferences(
            TenantId tenantId, UserId userId, AssistantId assistantId) {
        return preferences
                .findByTenantIdAndUserIdAndAssistantId(
                        tenantId.value(), userId.value(), assistantId.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private SourcePreference toDomain(ContextSourcePreferenceEntity entity) {
        return new SourcePreference(
                new TenantId(entity.getTenantId()),
                new UserId(entity.getUserId()),
                new AssistantId(entity.getAssistantId()),
                SourceType.valueOf(entity.getSourceType()),
                entity.getSourceKey(),
                Disposition.valueOf(entity.getDisposition()),
                entity.getReason(),
                entity.getUpdatedAt());
    }

    private static void add(
            LinkedHashMap<String, SourceReference> values, SourceReference reference) {
        values.put(key(reference.type(), reference.sourceKey()), reference);
    }

    private static String key(SourceType type, String sourceKey) {
        return type.name() + ':' + sourceKey;
    }
}
