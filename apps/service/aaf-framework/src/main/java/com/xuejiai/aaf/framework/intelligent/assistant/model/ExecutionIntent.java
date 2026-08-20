package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;
import java.util.Set;

/** 单次 Assistant 执行的不可变意图；固定 Route、产物和动作授权策略必须随执行画像冻结。 */
public record ExecutionIntent(
        InteractionMode interactionMode,
        RouteConstraint routeConstraint,
        ClarificationPolicy clarificationPolicy,
        ResolvedRoute resolvedRoute,
        ArtifactPolicy artifactPolicy,
        ActionAuthorizationPolicy actionAuthorizationPolicy,
        Long workspaceId) {

    public ExecutionIntent {
        Objects.requireNonNull(interactionMode, "interactionMode 不能为空");
        Objects.requireNonNull(routeConstraint, "routeConstraint 不能为空");
        Objects.requireNonNull(clarificationPolicy, "clarificationPolicy 不能为空");
        Objects.requireNonNull(artifactPolicy, "artifactPolicy 不能为空");
        Objects.requireNonNull(actionAuthorizationPolicy, "actionAuthorizationPolicy 不能为空");
        if (interactionMode == InteractionMode.TASK && routeConstraint != RouteConstraint.FIXED) {
            throw new IllegalArgumentException("TASK 执行必须使用 FIXED Route");
        }
        if (routeConstraint == RouteConstraint.FIXED) {
            Objects.requireNonNull(resolvedRoute, "FIXED Route 必须包含已解析路由");
        } else if (resolvedRoute != null) {
            throw new IllegalArgumentException("AUTO Route 不允许预置已解析路由");
        }
        if (artifactPolicy.persistenceMode() == PersistenceMode.AUTO_SAVE_DRAFT
                && actionAuthorizationPolicy.behavior(artifactPolicy.saveTool())
                        == ActionAuthorizationPolicy.MissingGrantBehavior.DENY) {
            throw new IllegalArgumentException("AUTO_SAVE_DRAFT 不能拒绝 saveTool 的授权动作");
        }
        if (artifactPolicy.persistenceMode() == PersistenceMode.AUTO_SAVE_DRAFT
                && !actionAuthorizationPolicy.requiresAuthorization(artifactPolicy.saveTool())) {
            throw new IllegalArgumentException("AUTO_SAVE_DRAFT 的 saveTool 必须声明动作授权");
        }
    }

    public static ExecutionIntent conversationalAuto(Long workspaceId) {
        return new ExecutionIntent(
                InteractionMode.CONVERSATIONAL,
                RouteConstraint.AUTO,
                ClarificationPolicy.INTERACTIVE,
                null,
                ArtifactPolicy.returnOnly(OutputKind.MESSAGE, "text/markdown"),
                ActionAuthorizationPolicy.requestOnDemand(),
                workspaceId);
    }

    public static ExecutionIntent taskFixed(
            String roleKey,
            String skillKey,
            long assistantRevision,
            ArtifactPolicy artifactPolicy,
            ActionAuthorizationPolicy actionAuthorizationPolicy,
            Long workspaceId) {
        return new ExecutionIntent(
                InteractionMode.TASK,
                RouteConstraint.FIXED,
                ClarificationPolicy.FAIL_ON_BLOCKER,
                new ResolvedRoute(roleKey, skillKey, assistantRevision),
                artifactPolicy,
                actionAuthorizationPolicy,
                workspaceId);
    }

    public boolean autoSaveDraft() {
        return artifactPolicy.persistenceMode() == PersistenceMode.AUTO_SAVE_DRAFT;
    }

    public enum InteractionMode {
        TASK,
        CONVERSATIONAL
    }

    public enum RouteConstraint {
        FIXED,
        AUTO
    }

    public enum ClarificationPolicy {
        MINIMAL,
        FAIL_ON_BLOCKER,
        INTERACTIVE
    }

    public enum OutputKind {
        MESSAGE,
        DOCUMENT
    }

    public enum PersistenceMode {
        RETURN_ONLY,
        AUTO_SAVE_DRAFT
    }

    public enum PublishPolicy {
        NEVER_BY_DEFAULT
    }

    public record ResolvedRoute(String roleKey, String skillKey, long assistantRevision) {
        public ResolvedRoute {
            roleKey = requireText(roleKey, "roleKey");
            skillKey = requireText(skillKey, "skillKey");
            if (assistantRevision < 0) {
                throw new IllegalArgumentException("assistantRevision 不能小于 0");
            }
        }
    }

    /** 本次执行对声明动作的缺失授权处理策略；与澄清策略完全独立。 */
    public record ActionAuthorizationPolicy(Mode mode, Set<String> actions) {
        public ActionAuthorizationPolicy {
            Objects.requireNonNull(mode, "mode 不能为空");
            actions = Set.copyOf(Objects.requireNonNull(actions, "actions 不能为空"));
            if (actions.stream().anyMatch(action -> normalize(action) == null)) {
                throw new IllegalArgumentException("授权动作不能为空白");
            }
        }

        public static ActionAuthorizationPolicy requestOnDemand(String... actions) {
            return new ActionAuthorizationPolicy(Mode.REQUEST_ON_DEMAND, Set.of(actions));
        }

        public static ActionAuthorizationPolicy preauthorizedOnly(String... actions) {
            return new ActionAuthorizationPolicy(Mode.PREAUTHORIZED_ONLY, Set.of(actions));
        }

        public static ActionAuthorizationPolicy denyAuthorizedActions(String... actions) {
            return new ActionAuthorizationPolicy(Mode.DENY_AUTHORIZED_ACTIONS, Set.of(actions));
        }

        public MissingGrantBehavior behavior(String action) {
            if (!actions.contains(action)) {
                return MissingGrantBehavior.DEFAULT;
            }
            return switch (mode) {
                case REQUEST_ON_DEMAND -> MissingGrantBehavior.REQUEST_ON_DEMAND;
                case PREAUTHORIZED_ONLY -> MissingGrantBehavior.PREAUTHORIZED_ONLY;
                case DENY_AUTHORIZED_ACTIONS -> MissingGrantBehavior.DENY;
            };
        }

        public boolean requiresAuthorization(String action) {
            return behavior(action) != MissingGrantBehavior.DEFAULT;
        }

        public enum Mode {
            REQUEST_ON_DEMAND,
            PREAUTHORIZED_ONLY,
            DENY_AUTHORIZED_ACTIONS
        }

        public enum MissingGrantBehavior {
            DEFAULT,
            REQUEST_ON_DEMAND,
            PREAUTHORIZED_ONLY,
            DENY
        }
    }

    public record ArtifactPolicy(
            OutputKind outputKind,
            String canonicalMediaType,
            PersistenceMode persistenceMode,
            String saveTool,
            PublishPolicy publishPolicy) {
        public ArtifactPolicy {
            Objects.requireNonNull(outputKind, "outputKind 不能为空");
            canonicalMediaType = requireText(canonicalMediaType, "canonicalMediaType");
            Objects.requireNonNull(persistenceMode, "persistenceMode 不能为空");
            Objects.requireNonNull(publishPolicy, "publishPolicy 不能为空");
            saveTool = normalize(saveTool);
            if (persistenceMode == PersistenceMode.AUTO_SAVE_DRAFT && saveTool == null) {
                throw new IllegalArgumentException("AUTO_SAVE_DRAFT 必须指定 saveTool");
            }
            if (persistenceMode == PersistenceMode.RETURN_ONLY && saveTool != null) {
                throw new IllegalArgumentException("RETURN_ONLY 不允许指定 saveTool");
            }
        }

        public static ArtifactPolicy autoSaveDraft(
                OutputKind outputKind, String canonicalMediaType, String saveTool) {
            return new ArtifactPolicy(
                    outputKind,
                    canonicalMediaType,
                    PersistenceMode.AUTO_SAVE_DRAFT,
                    saveTool,
                    PublishPolicy.NEVER_BY_DEFAULT);
        }

        public static ArtifactPolicy returnOnly(OutputKind outputKind, String canonicalMediaType) {
            return new ArtifactPolicy(
                    outputKind,
                    canonicalMediaType,
                    PersistenceMode.RETURN_ONLY,
                    null,
                    PublishPolicy.NEVER_BY_DEFAULT);
        }
    }

    private static String requireText(String value, String field) {
        var normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
