package com.xuejiai.aaf.framework.security.authorization;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次授权所声明的 L1-L4 执行计划。L1 必须显式声明，其余层按需声明。 */
public record AuthorizationPlan(
        FunctionRequirement l1, RelationPlan l2, DataPlan l3, PolicyPlan l4) {

    public AuthorizationPlan {
        Objects.requireNonNull(l1, "l1");
    }

    public static AuthorizationPlan functionPermission(String permissionCode) {
        return new AuthorizationPlan(
                FunctionRequirement.permission(permissionCode), null, null, null);
    }

    public static AuthorizationPlan authenticated() {
        return new AuthorizationPlan(FunctionRequirement.authenticated(), null, null, null);
    }

    public enum CombinationMode {
        ALL_APPLICABLE,
        ANY_APPLICABLE
    }

    public enum FunctionMode {
        AUTHENTICATED,
        PERMISSION,
        ALL
    }

    /** L1 功能要求：认证不带权限码，单权限恰好一项，ALL 至少两项且全部满足。 */
    public record FunctionRequirement(FunctionMode mode, List<String> permissionCodes) {
        public FunctionRequirement {
            Objects.requireNonNull(mode, "mode");
            var declaredCodes = permissionCodes == null ? List.<String>of() : permissionCodes;
            if (declaredCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
                throw new IllegalArgumentException("L1 权限码不能为空");
            }
            permissionCodes = List.copyOf(declaredCodes);
            if (permissionCodes.stream().distinct().count() != permissionCodes.size()) {
                throw new IllegalArgumentException("L1 权限码不得重复");
            }
            switch (mode) {
                case AUTHENTICATED -> {
                    if (!permissionCodes.isEmpty()) {
                        throw new IllegalArgumentException("L1 AUTHENTICATED 不接受权限码");
                    }
                }
                case PERMISSION -> {
                    if (permissionCodes.size() != 1) {
                        throw new IllegalArgumentException("L1 PERMISSION 必须且只能声明一个权限码");
                    }
                }
                case ALL -> {
                    if (permissionCodes.size() < 2) {
                        throw new IllegalArgumentException("L1 ALL 至少声明两个权限码");
                    }
                }
            }
        }

        public static FunctionRequirement authenticated() {
            return new FunctionRequirement(FunctionMode.AUTHENTICATED, List.of());
        }

        public static FunctionRequirement permission(String permissionCode) {
            return new FunctionRequirement(
                    FunctionMode.PERMISSION,
                    java.util.Collections.singletonList(permissionCode));
        }

        public static FunctionRequirement all(String first, String second, String... remaining) {
            var codes = new java.util.ArrayList<String>();
            codes.add(first);
            codes.add(second);
            codes.addAll(List.of(remaining));
            return new FunctionRequirement(FunctionMode.ALL, codes);
        }
    }

    public record RelationPlan(
            CombinationMode combination, List<RelationRequirement> requirements) {
        public RelationPlan {
            Objects.requireNonNull(combination, "combination");
            requirements = List.copyOf(requirements);
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("已声明的 L2 计划至少包含一项要求");
            }
        }

        public static RelationPlan all(RelationRequirement... requirements) {
            return new RelationPlan(CombinationMode.ALL_APPLICABLE, List.of(requirements));
        }

        public static RelationPlan any(RelationRequirement... requirements) {
            return new RelationPlan(CombinationMode.ANY_APPLICABLE, List.of(requirements));
        }
    }

    public record RelationRequirement(String objectType, String objectId, String permission) {
        public RelationRequirement {
            requireText(objectType, "objectType");
            requireText(objectId, "objectId");
            requireText(permission, "permission");
        }
    }

    public record DataPlan(CombinationMode combination, List<DataRequirement> requirements) {
        public DataPlan {
            Objects.requireNonNull(combination, "combination");
            requirements = List.copyOf(requirements);
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("已声明的 L3 计划至少包含一项要求");
            }
        }

        public static DataPlan all(DataRequirement... requirements) {
            return new DataPlan(CombinationMode.ALL_APPLICABLE, List.of(requirements));
        }

        public static DataPlan any(DataRequirement... requirements) {
            return new DataPlan(CombinationMode.ANY_APPLICABLE, List.of(requirements));
        }
    }

    /** L3 要求由业务 Provider 按 key 解释，参数必须为受限且深度不可变的纯数据。 */
    public record DataRequirement(String key, Map<String, Object> parameters) {
        public DataRequirement {
            requireText(key, "key");
            parameters =
                    parameters == null
                            ? Map.of()
                            : AuthorizationRequest.immutableData(parameters);
        }

        public static DataRequirement of(String key) {
            return new DataRequirement(key, Map.of());
        }
    }

    /** 显式声明启用 L4；未声明时不得加载策略 Provider。 */
    public record PolicyPlan() {}

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
