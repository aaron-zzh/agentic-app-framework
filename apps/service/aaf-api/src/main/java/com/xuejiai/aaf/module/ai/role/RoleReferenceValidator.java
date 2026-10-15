package com.xuejiai.aaf.module.ai.role;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogEntry;
import com.xuejiai.aaf.framework.engine.tool.ToolCatalogProvider;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
import com.xuejiai.aaf.module.ai.skill.SkillAccessQuery;

import lombok.RequiredArgsConstructor;

/**
 * Role 引用完整性校验：保存、发布时校验 {@code skill_ids} 与 {@code tool_whitelist}。
 *
 * <p>规则：
 *
 * <ul>
 *   <li>技能必须存在且有已批准的当前版本；SYSTEM / PUBLIC 角色只能引用全员可引用的技能（内置、PUBLIC、INTERNAL）， PRIVATE
 *       角色额外可引用所有者自己的私有技能；任何角色不得引用 WORKSPACE 技能
 *   <li>工具必须是已启用的内置工具或目录登记且启用的工具，不按风险等级限制；PRIVATE 角色要求所有者持有工具声明的已注册权限码
 * </ul>
 *
 * <p>校验失败整体拒绝，列出越权的 {@code skillKey} 与工具名，不做部分保存，不降级放行。
 */
@Component
@RequiredArgsConstructor
public class RoleReferenceValidator {

    private final SkillAccessQuery skillAccess;
    private final ToolCatalogProvider toolCatalog;
    private final FunctionPermissionChecker permissions;
    private final ObjectProvider<ContextAwareToolHandler> builtinHandlers;

    /**
     * 校验角色引用。
     *
     * @param level 角色可见性，只接受 SYSTEM / PUBLIC / PRIVATE
     * @param ownerId PRIVATE 角色的所有者；SYSTEM / PUBLIC 传 null
     * @param skillBindings 技能绑定
     * @param toolWhitelistJson 工具授权池 JSON 数组
     */
    public void validate(
            RoleVisibilityLevel level,
            Long ownerId,
            List<SkillBinding> skillBindings,
            String toolWhitelistJson) {
        Objects.requireNonNull(level, "level 不能为空");
        var skillViolations = skillViolations(level, ownerId, skillBindings);
        var toolViolations = toolViolations(level, ownerId, toolNames(toolWhitelistJson));
        if (skillViolations.isEmpty() && toolViolations.isEmpty()) {
            return;
        }
        var message = new StringBuilder("角色引用校验未通过");
        if (!skillViolations.isEmpty()) {
            message.append("；技能：").append(String.join("、", skillViolations));
        }
        if (!toolViolations.isEmpty()) {
            message.append("；工具：").append(String.join("、", toolViolations));
        }
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, message.toString());
    }

    private List<String> skillViolations(
            RoleVisibilityLevel level, Long ownerId, List<SkillBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        var keys = bindings.stream().map(SkillBinding::skillKey).distinct().toList();
        var found = skillAccess.findByCodes(keys);
        var violations = new LinkedHashSet<String>();
        for (var key : keys) {
            var access = found.get(key);
            if (access == null) {
                violations.add(key + "（不存在）");
            } else if (!access.published()) {
                violations.add(key + "（没有已批准的当前版本）");
            } else if ("WORKSPACE".equals(access.visibility())) {
                violations.add(key + "（工作区技能不支持被角色引用）");
            } else if (access.shared()) {
                continue;
            } else if (level == RoleVisibilityLevel.PRIVATE && access.privateOf(ownerId)) {
                continue;
            } else if (level == RoleVisibilityLevel.PRIVATE) {
                violations.add(key + "（不是你可见的技能）");
            } else {
                violations.add(key + "（私有技能不能被内置或公开角色引用）");
            }
        }
        return List.copyOf(violations);
    }

    private List<String> toolViolations(
            RoleVisibilityLevel level, Long ownerId, Set<String> toolNames) {
        var violations = new LinkedHashSet<String>();
        for (var name : toolNames) {
            if (isBuiltin(name)) {
                continue;
            }
            var entry = toolCatalog.find(name);
            if (entry.isEmpty()) {
                violations.add(name + "（未登记）");
            } else if (!entry.get().enabled()) {
                violations.add(name + "（已停用）");
            } else if (level == RoleVisibilityLevel.PRIVATE && !permitted(entry.get(), ownerId)) {
                violations.add(name + "（你没有该工具所需权限）");
            }
        }
        return List.copyOf(violations);
    }

    /** 工具声明了已注册的权限码时，所有者必须全部持有；与 ToolVisibilityFilter 同口径。 */
    private boolean permitted(ToolCatalogEntry entry, Long ownerId) {
        var declared = entry.permissionCode();
        if (declared == null || declared.isBlank()) {
            return true;
        }
        var registered =
                Arrays.stream(declared.split(","))
                        .map(String::trim)
                        .filter(code -> !code.isBlank())
                        .filter(permissions::isRegistered)
                        .toList();
        if (registered.isEmpty()) {
            return true;
        }
        return ownerId != null
                && registered.stream().allMatch(code -> permissions.hasPermission(ownerId, code));
    }

    private boolean isBuiltin(String toolName) {
        return builtinHandlers.stream().anyMatch(handler -> handler.toolName().equals(toolName));
    }

    /**
     * 用户创建私有角色时可选的工具：内置工具加目录中已启用、且用户持有所需权限的工具。
     *
     * @param name 工具名
     * @param category 工具分类，内置工具为空
     * @param riskLevel 风险等级，内置工具为空；仅作展示，不影响可选性
     * @param requireConfirm 调用时是否需要人工确认
     * @param readOnly 是否只读
     */
    public record SelectableTool(
            String name,
            String category,
            String riskLevel,
            boolean requireConfirm,
            boolean readOnly) {}

    /** 列出用户可选的工具，按工具名排序、去重。 */
    public List<SelectableTool> selectableTools(Long ownerId) {
        var tools = new TreeMap<String, SelectableTool>();
        builtinHandlers.stream()
                .forEach(
                        handler ->
                                tools.put(
                                        handler.toolName(),
                                        new SelectableTool(
                                                handler.toolName(),
                                                null,
                                                null,
                                                handler.requireConfirm(),
                                                handler.readOnly())));
        for (var entry : toolCatalog.listEnabled()) {
            if (!permitted(entry, ownerId)) {
                continue;
            }
            tools.putIfAbsent(
                    entry.toolName(),
                    new SelectableTool(
                            entry.toolName(),
                            entry.category(),
                            entry.riskLevel() == null ? null : entry.riskLevel().name(),
                            entry.requireConfirm(),
                            entry.readOnly()));
        }
        return List.copyOf(tools.values());
    }

    private static Set<String> toolNames(String toolWhitelistJson) {
        if (toolWhitelistJson == null || toolWhitelistJson.isBlank()) {
            return Set.of();
        }
        try {
            var names = new LinkedHashSet<String>();
            var root = JsonUtils.readTreeStrict(toolWhitelistJson);
            if (!root.isArray()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工具授权池必须是 JSON 字符串数组");
            }
            for (var item : root) {
                if (!item.isString() || item.asString().isBlank()) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工具授权池必须是 JSON 字符串数组");
                }
                names.add(item.asString().trim());
            }
            return names;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException failure) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工具授权池必须是合法 JSON 字符串数组");
        }
    }
}
