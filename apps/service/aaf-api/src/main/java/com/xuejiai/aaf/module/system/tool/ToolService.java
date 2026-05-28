package com.xuejiai.aaf.module.system.tool;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRegistry.ToolMeta;

import lombok.RequiredArgsConstructor;

/**
 * 工具管理服务——对外提供工具查询和调用能力
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final ToolCallDispatcher toolCallDispatcher;
    private final ToolPermissionChecker permissionChecker;

    /**
     * 查询所有已注册工具
     *
     * @param source 工具来源过滤（LOCAL/MCP/CUSTOM），为 null 时查全部
     * @return 工具列表
     */
    public List<ToolVO> list(String source) {
        var tools = source != null ? toolRegistry.listBySource(source) : toolRegistry.listAll();
        return tools.stream().map(this::toVO).toList();
    }

    /**
     * 调用工具（统一入口，不管调用方是谁）
     *
     * @param toolName 工具名称
     * @param arguments 调用参数（JSON 字符串）
     * @return 工具调用结果
     */
    public ToolCallResult invoke(String toolName, String arguments) {
        if (toolRegistry.getCallback(toolName).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工具未注册: " + toolName);
        }
        return toolCallDispatcher.dispatch(toolName, arguments);
    }

    /**
     * 按 Role 获取可用工具列表
     *
     * @param roleId 角色 ID
     * @return 该角色白名单内的工具列表
     */
    public List<ToolVO> listByRole(String roleId) {
        return toolRegistry.resolveForRole(roleId).stream()
                .map(
                        cb -> {
                            var name = cb.getToolDefinition().name();
                            var meta =
                                    toolRegistry.listAll().stream()
                                            .filter(m -> m.name().equals(name))
                                            .findFirst()
                                            .orElse(
                                                    new ToolMeta(
                                                            name,
                                                            cb.getToolDefinition().description(),
                                                            "UNKNOWN",
                                                            null));
                            return toVO(meta);
                        })
                .toList();
    }

    private ToolVO toVO(ToolMeta meta) {
        return new ToolVO(meta.name(), meta.description(), meta.source(), meta.parametersSchema());
    }

    /**
     * 用户批准工具调用权限（临时授权）
     *
     * @param sessionId 会话 ID
     * @param toolName 工具名称
     */
    public void approve(String sessionId, String toolName) {
        permissionChecker.grantTemporary(sessionId, toolName);
    }
}
