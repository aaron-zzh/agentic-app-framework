package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolCatalogPort;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;

/**
 * 工具可见性的用户权限维度。
 *
 * <p>Skill × Role/Assistant × Agent 的交集由 {@link EffectiveToolResolver} 计算；本过滤器只补最后一维——目录声明的 {@code
 * permissionCode} 与执行用户实际持有的功能权限求交集，使模型看不到该用户无权使用的工具。
 *
 * <p>权限码语义：{@code ai_tool_catalog.permission_code} 是可选声明，**未注册的权限码不构成授权契约**（历史数据里存在只写在 目录、从未注册进
 * {@code sys_permission_code} 的码），因此只有已注册的权限码才参与收窄；已注册但用户不持有即不可见。 {@code permissionCode} 为空、或
 * {@code userId} 不是有效数字且没有任何已注册权限要求时保持可见，可见性交给上游交集。
 * 多个权限码以逗号分隔时要求全部持有。收紧的正确顺序是先补注册与角色分配，再由数据驱动收窄，而不是由代码替缺失的数据做安全决策。
 *
 * <p>与 {@code AgentScopeToolkitFactory} 采用同一分流规则：内置 {@link ContextAwareToolHandler} 的元数据由代码自声明、 不经过
 * {@code ai_tool_catalog}，因此不查目录也不参与权限码收窄；只有走目录的工具（Connector/MCP 与已登记的本地工具） 才按目录声明的权限码判定。
 *
 * <p>本过滤器只影响可见性，不改变调用期授权行为：调用期的 grant、策略与确认门仍由 {@code DefaultToolGateway} 负责。
 */
public final class ToolVisibilityFilter {

    private final ToolCatalogPort toolCatalog;
    private final FunctionPermissionChecker permissions;
    private final ObjectProvider<ContextAwareToolHandler> builtinHandlers;

    public ToolVisibilityFilter(
            ToolCatalogPort toolCatalog,
            FunctionPermissionChecker permissions,
            ObjectProvider<ContextAwareToolHandler> builtinHandlers) {
        this.toolCatalog = Objects.requireNonNull(toolCatalog, "toolCatalog 不能为空");
        this.permissions = Objects.requireNonNull(permissions, "permissions 不能为空");
        this.builtinHandlers = Objects.requireNonNull(builtinHandlers, "builtinHandlers 不能为空");
    }

    /** 按执行用户持有的功能权限收窄可见工具；输入顺序保持不变。 */
    public List<ToolRef> visible(List<ToolRef> tools, String userId) {
        Objects.requireNonNull(tools, "tools 不能为空");
        if (tools.isEmpty()) {
            return List.of();
        }
        var catalogRefs = tools.stream().filter(tool -> !builtin(tool.name())).toList();
        if (catalogRefs.isEmpty()) {
            return List.copyOf(tools);
        }
        var ownerId = numericUserId(userId);
        var requirementsByTool =
                toolCatalog.resolve(catalogRefs).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        definition -> definition.ref().name(),
                                        ToolVisibilityFilter::requiredPermissionCodes,
                                        (left, right) -> left));
        return tools.stream()
                .filter(
                        tool ->
                                allowed(
                                        requirementsByTool.getOrDefault(tool.name(), List.of()),
                                        ownerId))
                .toList();
    }

    private boolean builtin(String toolName) {
        return builtinHandlers.stream().anyMatch(handler -> handler.toolName().equals(toolName));
    }

    private boolean allowed(List<String> requiredPermissionCodes, Long ownerId) {
        var contractCodes =
                requiredPermissionCodes.stream().filter(permissions::isRegistered).toList();
        if (contractCodes.isEmpty()) {
            return true;
        }
        if (ownerId == null) {
            return false;
        }
        return contractCodes.stream().allMatch(code -> permissions.hasPermission(ownerId, code));
    }

    private static List<String> requiredPermissionCodes(ToolCatalogPort.ToolDefinition definition) {
        if (definition.permissionCode().isBlank()) {
            return List.of();
        }
        return Stream.of(definition.permissionCode().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static Long numericUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
